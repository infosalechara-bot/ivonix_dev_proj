import hashlib
import os
import secrets
from typing import Literal

import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

API_BASE = os.environ.get("PULSE_BACKEND_URL", "http://ontology:8081/api/v1").rstrip("/")
SERVICE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
ORG_ID = os.environ.get("PULSE_FOUNDER_ORGANIZATION_ID")
FOUNDER_USER_ID = os.environ.get("PULSE_FOUNDER_USER_ID")
AGENT_SHARED_SECRET = os.environ.get("PULSE_FOUNDER_AGENT_SECRET")

if not SERVICE_KEY:
    raise RuntimeError("SUPABASE_SERVICE_ROLE_KEY is required")
if not ORG_ID:
    raise RuntimeError("PULSE_FOUNDER_ORGANIZATION_ID is required")
if not FOUNDER_USER_ID:
    raise RuntimeError("PULSE_FOUNDER_USER_ID is required")

SUPABASE_URL = os.environ.get("SUPABASE_URL", "").rstrip("/")
if not SUPABASE_URL:
    raise RuntimeError("SUPABASE_URL is required")

app = FastAPI(title="PULSE Founder Agent", version="FND-026")

class Alert(BaseModel):
    message: str = Field(min_length=1, max_length=10000)
    urgency: Literal["normal", "important", "critical"] = "normal"

class Confirmation(BaseModel):
    action: str = Field(min_length=1, max_length=2000)


def headers():
    return {
        "apikey": SERVICE_KEY,
        "Authorization": f"Bearer {SERVICE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "return=representation",
    }


def require_agent_secret(secret: str | None):
    if not AGENT_SHARED_SECRET or not secret or not secrets.compare_digest(secret, AGENT_SHARED_SECRET):
        raise HTTPException(status_code=403, detail="Founder agent authorization required")


def store_message(text: str, urgency: str):
    r = requests.post(
        f"{SUPABASE_URL}/rest/v1/founder_messages",
        headers=headers(),
        json={"organization_id": ORG_ID, "sender": "pulse_agent", "message_text": text, "message_type": "signal", "urgency": urgency},
        timeout=10,
    )
    r.raise_for_status()
    return r.json()


def create_confirmation(action: str):
    raw = secrets.token_urlsafe(32)
    token_hash = hashlib.sha256(raw.encode()).hexdigest()
    r = requests.post(
        f"{SUPABASE_URL}/rest/v1/executive_confirmations",
        headers=headers(),
        json={"organization_id": ORG_ID, "requested_action": action, "requested_by": FOUNDER_USER_ID, "confirmation_token_hash": token_hash},
        timeout=10,
    )
    r.raise_for_status()
    store_message(f"Confirmation required: {action}", "critical")
    record = r.json()[0] if isinstance(r.json(), list) else r.json()
    return {"confirmation_id": record["id"], "confirmation_token": raw, "status": record["status"]}


@app.get("/health")
def health():
    return {"status": "ok", "service": "pulse-founder-agent", "series": "FND-026"}

@app.post("/alert")
def alert(payload: Alert, x_founder_agent_secret: str | None = None):
    require_agent_secret(x_founder_agent_secret)
    return store_message(payload.message, payload.urgency)

@app.post("/confirmation")
def confirmation(payload: Confirmation, x_founder_agent_secret: str | None = None):
    require_agent_secret(x_founder_agent_secret)
    return create_confirmation(payload.action)
