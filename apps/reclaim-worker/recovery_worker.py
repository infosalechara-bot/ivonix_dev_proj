import hashlib
import hmac
import os
import shutil
import subprocess
from pathlib import Path
from typing import Optional

import requests
from fastapi import BackgroundTasks, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="PULSE RECLAIM Worker", version="1.2")
SUPABASE_URL = os.environ["SUPABASE_URL"].rstrip("/")
SERVICE_KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]
WORKER_SECRET = os.environ.get("PULSE_RECLAIM_WORKER_SECRET", "")
if not WORKER_SECRET: raise RuntimeError("PULSE_RECLAIM_WORKER_SECRET is required")
ROOT = Path(os.environ.get("PULSE_RECLAIM_ROOT", "/reclaim-inputs")).resolve()
OUTPUT_ROOT = Path(os.environ.get("PULSE_RECLAIM_OUTPUT", "/reclaim-output")).resolve()

class RecoveryRequest(BaseModel):
    jobId: str
    deviceId: Optional[str] = None
    jobType: str = Field(pattern="^(file|message|database|system)$")
    targetPath: Optional[str] = None
    targetTable: Optional[str] = None
    targetIdentifier: Optional[str] = None

def headers(): return {"apikey": SERVICE_KEY, "Authorization": f"Bearer {SERVICE_KEY}", "Content-Type": "application/json"}
def api(method, table, **kwargs):
    r = requests.request(method, f"{SUPABASE_URL}/rest/v1/{table}", headers=headers(), timeout=30, **kwargs); r.raise_for_status(); return r

def get_job(job_id: str):
    r = api("GET", "recovery_jobs", params={"id": f"eq.{job_id}", "select": "id,organization_id,device_id,job_type,target_path,target_table,target_identifier,status"})
    rows = r.json()
    if len(rows) != 1: raise ValueError("Recovery job not found")
    return rows[0]

def validate_request_matches_job(req: RecoveryRequest, job: dict):
    persisted = {"deviceId": job.get("device_id"), "jobType": job.get("job_type"), "targetPath": job.get("target_path"), "targetTable": job.get("target_table"), "targetIdentifier": job.get("target_identifier")}
    for field in persisted:
        if getattr(req, field) != persisted[field]: raise PermissionError(f"Recovery request does not match persisted job: {field}")
    if job.get("status") not in ("queued", "scanning"): raise PermissionError("Recovery job is not runnable in its current state")
    return job

def log(job_id, step, details): api("POST", "recovery_logs", json={"recovery_job_id": job_id, "step": step, "details": details})
def update(job_id, status, progress, summary=None):
    payload={"status":status,"progress":progress}
    if summary is not None: payload["result_summary"]=summary
    api("PATCH","recovery_jobs",params={"id":f"eq.{job_id}"},json=payload)
def safe_source(value: str) -> Path:
    p=Path(value).expanduser().resolve()
    if p!=ROOT and ROOT not in p.parents: raise ValueError("targetPath is outside the configured PULSE_RECLAIM_ROOT")
    if not p.exists(): raise FileNotFoundError("Recovery source does not exist")
    return p
def command_available(name: str) -> bool: return shutil.which(name) is not None
def run_foremost(source: Path, output: Path):
    if not command_available("foremost"): raise RuntimeError("foremost is not installed in the recovery worker")
    output.mkdir(parents=True, exist_ok=True)
    proc=subprocess.run(["foremost","-i",str(source),"-o",str(output),"-T"],capture_output=True,text=True,timeout=3600)
    if proc.returncode!=0: raise RuntimeError(proc.stderr[-2000:] or "foremost failed")
    return proc.stdout[-4000:]
def hash_file(path: Path) -> str:
    h=hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""): h.update(chunk)
    return h.hexdigest()
def collect_outputs(output: Path):
    items=[]
    for p in output.rglob("*"):
        if p.is_file(): items.append({"item_type":"file","original_location":str(p.relative_to(output)),"recovered_data":str(p),"confidence":0.70,"sha256":hash_file(p),"size":p.stat().st_size})
    return items
def recover_file_or_system(req: RecoveryRequest, output: Path):
    source=safe_source(req.targetPath or "")
    if source.is_dir(): return [], {"mode":"inventory_only","reason":"A disk image or raw forensic source is required for deleted-file carving"}
    return collect_outputs(output), {"mode":"foremost","tool_output":run_foremost(source,output)}
def recover_message(req: RecoveryRequest): return [], {"mode":"not_configured","reason":"Message WAL/log parser must be explicitly configured for the organization’s messaging system"}
def recover_database(req: RecoveryRequest):
    if not req.targetPath: return [], {"mode":"not_configured","reason":"A mounted WAL/backup source is required"}
    source=safe_source(req.targetPath)
    if not command_available("pg_waldump"): return [], {"mode":"not_configured","reason":"pg_waldump is not installed"}
    proc=subprocess.run(["pg_waldump","--path",str(source)],capture_output=True,text=True,timeout=3600)
    if proc.returncode!=0: raise RuntimeError(proc.stderr[-2000:] or "pg_waldump failed")
    return [], {"mode":"pg_waldump","analysis":"WAL decoded; row reconstruction requires schema-aware parser","tool_output":proc.stdout[-4000:]}
def insert_item(job_id,item): api("POST","recovered_items",json={"recovery_job_id":job_id,"item_type":item["item_type"],"original_location":item.get("original_location"),"recovered_data":item.get("recovered_data"),"confidence":item.get("confidence")})
def run_recovery(req: RecoveryRequest, persisted_job: dict):
    output=OUTPUT_ROOT/req.jobId
    try:
        update(req.jobId,"scanning",10); log(req.jobId,"scan_started",{"job_type":req.jobType,"organization_id":persisted_job.get("organization_id")})
        if req.jobType in ("file","system"): items,summary=recover_file_or_system(req,output)
        elif req.jobType=="message": items,summary=recover_message(req)
        else: items,summary=recover_database(req)
        for i,item in enumerate(items): insert_item(req.jobId,item); update(req.jobId,"scanning",min(95,20+int(75*(i+1)/max(1,len(items)))))
        result={"recovered_count":len(items),**summary}; update(req.jobId,"completed",100,result); log(req.jobId,"recovery_completed",result)
    except Exception as exc:
        result={"error":str(exc)[:2000]}
        try: update(req.jobId,"failed",100,result); log(req.jobId,"recovery_failed",result)
        except Exception: pass

@app.get("/health")
def health(): return {"status":"ok","service":"PULSE RECLAIM"}
@app.post("/recover")
async def recover(req: RecoveryRequest, background_tasks: BackgroundTasks, x_pulse_reclaim_secret: Optional[str] = Header(default=None)):
    if x_pulse_reclaim_secret is None or not hmac.compare_digest(x_pulse_reclaim_secret, WORKER_SECRET): raise HTTPException(status_code=401, detail="Unauthorized")
    try: persisted_job = validate_request_matches_job(req, get_job(req.jobId))
    except PermissionError as exc: raise HTTPException(status_code=403, detail=str(exc))
    except (ValueError, requests.RequestException) as exc: raise HTTPException(status_code=409, detail="Recovery job could not be validated") from exc
    background_tasks.add_task(run_recovery, req, persisted_job)
    return {"status":"started","jobId":req.jobId}
