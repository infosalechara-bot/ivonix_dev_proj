import hashlib
import os
import secrets
from typing import Literal

import requests
from fastapi import FastAPI, HTTPException, Header
from pydantic import BaseModel, Field

SERVICE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
ORG_ID = os.environ.get("PULSE_FOUNDER_ORGANIZATION_ID")
FOUNDER_USER_ID = os.environ.get("PULSE_FOUNDER_USER_ID")
AGENT_SHARED_SECRET = os.environ.get("PULSE_FOUNDER_AGENT_SECRET")
SUPABASE_URL = os.environ.get("SUPABASE_URL", "").rstrip("/")

for name, value in (("SUPABASE_SERVICE_ROLE_KEY", SERVICE_KEY), ("PULSE_FOUNDER_ORGANIZATION_ID", ORG_ID), ("PULSE_FOUNDER_USER_ID", FOUNDER_USER_ID), ("PULSE_FOUNDER_AGENT_SECRET", AGENT_SHARED_SECRET), ("SUPABASE_URL", SUPABASE_URL)):
    if not value:
        raise RuntimeError(f"{name} is required")

app = FastAPI(title="PULSE Founder Agent", version="FND-026")

class Alert(BaseModel):
    message: str = Field(min_length=1, max_length=10000)
    urgency: Literal["normal", "important", "critical"] = "normal"

class Confirmation(BaseModel):
    action: str = Field(min_length=1, max_length=2000)


def headers():
    return {"apikey": SERVICE_KEY, "Authorization": f"Bearer {SERVICE_KEY}", "Content-Type": "application/json", "Prefer": "return=representation"}


def require_agent_secret(secret: str | None):
    if not secret or not secrets.compare_digest(secret, AGENT_SHARED_SECRET):
        raise HTTPException(status_code=403, detail="Founder agent authorization required")


def store_message(text: str, urgency: str):
    r = requests.post(f"{SUPABASE_URL}/rest/v1/founder_messages", headers=headers(), json={
        "organization_id": ORG_ID, "sender": "pulse_agent", "message_text": text, "message_type": "signal", "urgency": urgency
    }, timeout=10)
    r.raise_for_status()
    return r.json()


def create_confirmation(action: str):
    raw = secrets.token_urlsafe(32)
    token_hash = hashlib.sha256(raw.encode()).hexdigest()
    r = requests.post(f"{SUPABASE_URL}/rest/v1/executive_confirmations", headers=headers(), json={
        "organization_id": ORG_ID, "requested_action": action, "requested_by": FOUNDER_USER_ID, "confirmation_token_hash": token_hash
    }, timeout=10)
    r.raise_for_status()
    record = r.json()[0]
    store_message(f"Confirmation required: {action}", "critical")
    return {"confirmation_id": record["id"], "confirmation_token": raw, "status": record["status"]}

@app.get("/health")
def health():
    return {"status": "ok", "service": "pulse-founder-agent", "series": "FND-026"}

@app.post("/alert")
def alert(payload: Alert, x_founder_agent_secret: str | None = Header(default=None)):
    require_agent_secret(x_founder_agent_secret)
    return store_message(payload.message, payload.urgency)

@app.post("/confirmation")
def confirmation(payload: Confirmation, x_founder_agent_secret: str | None = Header(default=None)):
    require_agent_secret(x_founder_agent_secret)
    return create_confirmation(payload.action)
