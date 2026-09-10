from fastapi import FastAPI, HTTPException, Header
from pydantic import BaseModel, Field
import hashlib, hmac, os, re, requests

app=FastAPI(title="PULSE Insight NL-to-SQL", version="1.1.0")

class NLRequest(BaseModel):
    text:str=Field(min_length=1,max_length=5000)
    organization_id:str

ORG="organization_id = '{org}'"
UUID_RE=re.compile(r"^[0-9a-fA-F-]{36}$")


def require_service_secret(value: str | None):
    expected=os.getenv("PULSE_NL_TO_SQL_SERVICE_SECRET")
    if not expected:
        raise RuntimeError("PULSE_NL_TO_SQL_SERVICE_SECRET is not configured")
    if not value or not hmac.compare_digest(value, expected):
        raise HTTPException(status_code=401,detail="Service authentication required")


def build(text,org):
    t=text.lower().strip(); scope=ORG.format(org=org)
    if "device" in t and "online" in t:
        return f"SELECT id,name,status,organization_id FROM devices WHERE {scope} AND status = 'online' ORDER BY name LIMIT 1000"
    if "telemetry" in t and ("last hour" in t or "past hour" in t):
        return f"SELECT * FROM device_telemetry WHERE {scope} AND timestamp > now() - interval '1 hour' ORDER BY timestamp DESC LIMIT 5000"
    if "temperature" in t and ("last hour" in t or "past hour" in t):
        return f"SELECT * FROM device_telemetry WHERE {scope} AND timestamp > now() - interval '1 hour' AND temperature > 80 ORDER BY timestamp DESC LIMIT 5000"
    if "security" in t and "severity" in t:
        return f"SELECT severity,count(*) AS event_count FROM security_events WHERE {scope} AND created_at > now() - interval '7 days' GROUP BY severity ORDER BY event_count DESC"
    if "average" in t and "battery" in t:
        return f"SELECT avg(battery_level) AS average_battery_level FROM device_telemetry WHERE {scope} AND battery_level IS NOT NULL"
    if "diagnos" in t:
        return f"SELECT * FROM diagnostic_procedures WHERE {scope} ORDER BY created_at DESC LIMIT 1000"
    raise HTTPException(status_code=422,detail="I could not safely map that request to an allowed PULSE query. Try naming the module, metric, and time range.")


def log_history(text,sql,org):
    url=os.getenv("SUPABASE_URL"); key=os.getenv("SUPABASE_SERVICE_ROLE_KEY")
    if not url or not key: return
    try:
        headers={"apikey":key,"Authorization":f"Bearer {key}","Content-Type":"application/json"}
        requests.post(f"{url}/rest/v1/nl_query_history",json={"organization_id":org,"natural_text":text,"generated_sql":sql},headers=headers,timeout=3).raise_for_status()
    except requests.RequestException:
        pass


@app.get("/health")
def health(): return {"status":"ok","service":"pulse-insight-nl-to-sql"}


@app.post("/convert")
def convert(req:NLRequest, authorization: str | None = Header(default=None)):
    token=authorization.removeprefix("Bearer ").strip() if authorization else None
    require_service_secret(token)
    if not UUID_RE.fullmatch(req.organization_id):
        raise HTTPException(status_code=400,detail="Invalid organization id")
    sql=build(req.text,req.organization_id)
    log_history(req.text,sql,req.organization_id)
    return {"sql":sql,"internal":True}
