import hashlib
import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Optional

import requests
from fastapi import BackgroundTasks, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="PULSE RECLAIM Worker", version="1.0")

SUPABASE_URL = os.environ["SUPABASE_URL"].rstrip("/")
SERVICE_KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]
WORKER_SECRET = os.environ.get("PULSE_RECLAIM_WORKER_SECRET", "")
ROOT = Path(os.environ.get("PULSE_RECLAIM_ROOT", "/reclaim-inputs")).resolve()
OUTPUT_ROOT = Path(os.environ.get("PULSE_RECLAIM_OUTPUT", "/reclaim-output")).resolve()

class RecoveryRequest(BaseModel):
    jobId: str
    deviceId: Optional[str] = None
    jobType: str = Field(pattern="^(file|message|database|system)$")
    targetPath: Optional[str] = None
    targetTable: Optional[str] = None
    targetIdentifier: Optional[str] = None


def headers():
    return {"apikey": SERVICE_KEY, "Authorization": f"Bearer {SERVICE_KEY}", "Content-Type": "application/json"}


def api(method, table, **kwargs):
    r = requests.request(method, f"{SUPABASE_URL}/rest/v1/{table}", headers=headers(), timeout=30, **kwargs)
    r.raise_for_status()
    return r


def log(job_id, step, details):
    api("POST", "recovery_logs", json={"recovery_job_id": job_id, "step": step, "details": details})


def update(job_id, status, progress, summary=None):
    payload = {"status": status, "progress": progress}
    if summary is not None:
        payload["result_summary"] = summary
    api("PATCH", "recovery_jobs", params={"id": f"eq.{job_id}"}, json=payload)


def safe_source(value: str) -> Path:
    p = Path(value).expanduser().resolve()
    # Recovery workers operate only on explicitly mounted organization-owned inputs.
    if p != ROOT and ROOT not in p.parents:
        raise ValueError("targetPath is outside the configured PULSE_RECLAIM_ROOT")
    if not p.exists():
        raise FileNotFoundError("Recovery source does not exist")
    return p


def command_available(name: str) -> bool:
    return shutil.which(name) is not None


def run_foremost(source: Path, output: Path):
    if not command_available("foremost"):
        raise RuntimeError("foremost is not installed in the recovery worker")
    output.mkdir(parents=True, exist_ok=True)
    # Source is an organization-owned mounted image/file under the configured root.
    proc = subprocess.run(["foremost", "-i", str(source), "-o", str(output), "-T"],
                          capture_output=True, text=True, timeout=3600)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr[-2000:] or "foremost failed")
    return proc.stdout[-4000:]


def hash_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def collect_outputs(output: Path):
    items = []
    for p in output.rglob("*"):
        if p.is_file():
            rel = p.relative_to(output)
            items.append({
                "item_type": "file",
                "original_location": str(rel),
                "recovered_data": str(p),
                "confidence": 0.70,
                "sha256": hash_file(p),
                "size": p.stat().st_size,
            })
    return items


def recover_file_or_system(req: RecoveryRequest, output: Path):
    source = safe_source(req.targetPath or "")
    # Directory targets are inventory-only: file carving requires a disk image or raw source.
    if source.is_dir():
        return [], {"mode": "inventory_only", "reason": "A disk image or raw forensic source is required for deleted-file carving"}
    detail = run_foremost(source, output)
    return collect_outputs(output), {"mode": "foremost", "tool_output": detail}


def recover_message(req: RecoveryRequest):
    # Message recovery is connector-specific. Do not fabricate recovered content.
    return [], {"mode": "not_configured", "reason": "Message WAL/log parser must be explicitly configured for the organization’s messaging system"}


def recover_database(req: RecoveryRequest):
    # pg_waldump operates on PostgreSQL WAL files and does not by itself reconstruct application rows.
    # Keep this path explicit rather than guessing a database layout or returning synthetic records.
    if not req.targetPath:
        return [], {"mode": "not_configured", "reason": "A mounted WAL/backup source is required"}
    source = safe_source(req.targetPath)
    if not command_available("pg_waldump"):
        return [], {"mode": "not_configured", "reason": "pg_waldump is not installed"}
    proc = subprocess.run(["pg_waldump", "--path", str(source)], capture_output=True, text=True, timeout=3600)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr[-2000:] or "pg_waldump failed")
    return [], {"mode": "pg_waldump", "analysis": "WAL decoded; row reconstruction requires schema-aware parser", "tool_output": proc.stdout[-4000:]}


def insert_item(job_id, item):
    payload = {
        "recovery_job_id": job_id,
        "item_type": item["item_type"],
        "original_location": item.get("original_location"),
        "recovered_data": item.get("recovered_data"),
        "confidence": item.get("confidence"),
    }
    api("POST", "recovered_items", json=payload)


def run_recovery(req: RecoveryRequest):
    output = OUTPUT_ROOT / req.jobId
    try:
        update(req.jobId, "scanning", 10)
        log(req.jobId, "scan_started", {"job_type": req.jobType})
        if req.jobType in ("file", "system"):
            items, summary = recover_file_or_system(req, output)
        elif req.jobType == "message":
            items, summary = recover_message(req)
        else:
            items, summary = recover_database(req)
        for i, item in enumerate(items):
            insert_item(req.jobId, item)
            update(req.jobId, "scanning", min(95, 20 + int(75 * (i + 1) / max(1, len(items)))))
        result = {"recovered_count": len(items), **summary}
        update(req.jobId, "completed", 100, result)
        log(req.jobId, "recovery_completed", result)
    except Exception as exc:
        result = {"error": str(exc)[:2000]}
        try:
            update(req.jobId, "failed", 100, result)
            log(req.jobId, "recovery_failed", result)
        except Exception:
            pass


@app.get("/health")
def health():
    return {"status": "ok", "service": "PULSE RECLAIM"}


@app.post("/recover")
async def recover(req: RecoveryRequest, background_tasks: BackgroundTasks, x_pulse_reclaim_secret: Optional[str] = Header(default=None)):
    if WORKER_SECRET and x_pulse_reclaim_secret != WORKER_SECRET:
        raise HTTPException(status_code=401, detail="Unauthorized")
    background_tasks.add_task(run_recovery, req)
    return {"status": "started", "jobId": req.jobId}
