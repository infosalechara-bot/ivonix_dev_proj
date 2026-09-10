import base64
import hmac
import os

import requests
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from fastapi import Depends, FastAPI, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel

app = FastAPI(title="PULSE KEY Crypto Service", version="1.1")
URL = os.environ.get("SUPABASE_URL", "").rstrip("/")
SERVICE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")
MASTER_RAW = os.environ.get("PULSE_MASTER_KEY", "")
SERVICE_SECRET = os.environ.get("PULSE_CRYPTO_SERVICE_SECRET", "")
if not URL or not SERVICE_KEY or not MASTER_RAW:
    raise RuntimeError("SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY and PULSE_MASTER_KEY are required")
if not SERVICE_SECRET:
    raise RuntimeError("PULSE_CRYPTO_SERVICE_SECRET is required")
try:
    MASTER = base64.b64decode(MASTER_RAW)
except Exception as exc:
    raise RuntimeError("PULSE_MASTER_KEY must be base64") from exc
if len(MASTER) != 32:
    raise RuntimeError("PULSE_MASTER_KEY must decode to 32 bytes")
H = {"apikey": SERVICE_KEY, "Authorization": f"Bearer {SERVICE_KEY}", "Content-Type": "application/json"}
bearer = HTTPBearer(auto_error=True)


class Req(BaseModel):
    keyId: str
    data: str


def authorize(credentials: HTTPAuthorizationCredentials = Depends(bearer)):
    if not hmac.compare_digest(credentials.credentials, SERVICE_SECRET):
        raise HTTPException(401, "Invalid service credentials")


def row(key_id):
    r = requests.get(f"{URL}/rest/v1/crypto_keys", params={"id": f"eq.{key_id}", "status": "eq.active", "select": "id,key_type,encrypted_private_key"}, headers=H, timeout=5)
    r.raise_for_status()
    records = r.json()
    if len(records) != 1:
        raise HTTPException(404, "Key unavailable")
    return records[0]


def unwrap(value):
    raw = base64.b64decode(value)
    if len(raw) < 28:
        raise ValueError("Invalid key envelope")
    iv, ct = raw[:12], raw[12:]
    try:
        return AESGCM(MASTER).decrypt(iv, ct, b"pulse-key-v1")
    except Exception:
        # Backward compatibility for envelopes created before AAD versioning.
        return AESGCM(MASTER).decrypt(iv, ct, None)


def key_for(rec):
    if rec["key_type"] != "aes-256":
        raise HTTPException(400, "This endpoint currently supports AES-256 encryption keys only")
    try:
        key = unwrap(rec["encrypted_private_key"])
    except Exception as exc:
        raise HTTPException(500, "Key unwrap failed") from exc
    if len(key) != 32:
        raise HTTPException(500, "Invalid AES-256 key material")
    return key


def audit(key_id, op, status):
    try:
        requests.post(f"{URL}/rest/v1/crypto_operations", headers={**H, "Prefer": "return=minimal"}, json={"key_id": key_id, "operation_type": op, "status": status}, timeout=5).raise_for_status()
    except Exception:
        pass


@app.get("/health")
def health():
    return {"status": "ok", "hsm_backed": False, "mode": "encrypted-key-storage"}


@app.post("/v1/encrypt", dependencies=[Depends(authorize)])
def encrypt(req: Req):
    try:
        key = key_for(row(req.keyId))
        iv = os.urandom(12)
        ct = AESGCM(key).encrypt(iv, req.data.encode(), b"pulse-key-v1")
        audit(req.keyId, "encrypt", "success")
        return {"ciphertext": base64.b64encode(iv + ct).decode()}
    except HTTPException:
        audit(req.keyId, "encrypt", "failed")
        raise
    except Exception as exc:
        audit(req.keyId, "encrypt", "failed")
        raise HTTPException(500, "Encryption failed") from exc


@app.post("/v1/decrypt", dependencies=[Depends(authorize)])
def decrypt(req: Req):
    try:
        key = key_for(row(req.keyId))
        raw = base64.b64decode(req.data)
        if len(raw) < 28:
            raise ValueError("Invalid ciphertext")
        pt = AESGCM(key).decrypt(raw[:12], raw[12:], b"pulse-key-v1")
        audit(req.keyId, "decrypt", "success")
        return {"plaintext": pt.decode()}
    except HTTPException:
        audit(req.keyId, "decrypt", "failed")
        raise
    except Exception as exc:
        audit(req.keyId, "decrypt", "failed")
        raise HTTPException(400, "Decryption failed") from exc
