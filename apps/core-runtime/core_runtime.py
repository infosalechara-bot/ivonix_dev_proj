import hmac
import os
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import onnxruntime as ort
import requests
from fastapi import Depends, FastAPI, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel

app = FastAPI(title="PULSE CORE Runtime", version="0.2.0")
SUPABASE_URL = os.environ.get("SUPABASE_URL", "").rstrip("/")
SUPABASE_SERVICE_ROLE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")
CORE_SERVICE_SECRET = os.environ.get("PULSE_CORE_SERVICE_SECRET", "")
MODEL_ROOT = Path(os.environ.get("CORE_MODEL_ROOT", "/models")).resolve()
bearer = HTTPBearer(auto_error=False)

if not SUPABASE_URL or not SUPABASE_SERVICE_ROLE_KEY or not CORE_SERVICE_SECRET:
    raise RuntimeError("CORE runtime requires Supabase credentials and PULSE_CORE_SERVICE_SECRET")

class InferenceRequest(BaseModel):
    organization_id: str
    job_id: str
    model_id: str
    input_data: dict[str, Any]


def require_service(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)) -> str:
    if credentials is None or credentials.scheme.lower() != "bearer" or not hmac.compare_digest(credentials.credentials, CORE_SERVICE_SECRET):
        raise HTTPException(status_code=401, detail="Unauthorized")
    return "pulse-core"


def supabase_request(method: str, path: str, **kwargs):
    headers = kwargs.pop("headers", {})
    headers.update({"apikey": SUPABASE_SERVICE_ROLE_KEY, "Authorization": f"Bearer {SUPABASE_SERVICE_ROLE_KEY}"})
    response = requests.request(method, f"{SUPABASE_URL}/rest/v1/{path}", headers=headers, timeout=20, **kwargs)
    response.raise_for_status()
    return response


def fetch_job(job_id: str, organization_id: str):
    response = supabase_request("GET", "inference_jobs", params={
        "id": f"eq.{job_id}", "organization_id": f"eq.{organization_id}",
        "select": "id,organization_id,model_id"
    })
    rows = response.json()
    if len(rows) != 1:
        raise RuntimeError("Inference job not found")
    return rows[0]


def fetch_model(model_id: str, organization_id: str):
    response = supabase_request("GET", "core_models", params={
        "id": f"eq.{model_id}", "organization_id": f"eq.{organization_id}",
        "select": "id,organization_id,model_path,input_shape,output_shape,framework"
    })
    rows = response.json()
    if len(rows) != 1:
        raise RuntimeError("Model not found")
    return rows[0]


def update_job(organization_id: str, job_id: str, status: str, output=None, latency_ms=None, started=False, completed=False):
    payload: dict[str, Any] = {"status": status}
    if started:
        payload["started_at"] = datetime.now(timezone.utc).isoformat()
    if completed:
        payload["completed_at"] = datetime.now(timezone.utc).isoformat()
    if output is not None:
        payload["output_data"] = output
    if latency_ms is not None:
        payload["latency_ms"] = latency_ms
    response = supabase_request("PATCH", "inference_jobs", params={
        "id": f"eq.{job_id}", "organization_id": f"eq.{organization_id}"
    }, json=payload)
    if response.status_code not in (200, 204):
        raise RuntimeError("Unable to update inference job")


def resolve_model_path(model_path: str) -> Path:
    path = Path(model_path)
    if not path.is_absolute():
        path = MODEL_ROOT / path
    path = path.resolve()
    if MODEL_ROOT not in path.parents and path != MODEL_ROOT:
        raise RuntimeError("Model path escapes CORE_MODEL_ROOT")
    if not path.is_file():
        raise RuntimeError("Model file is unavailable to the CORE runtime")
    return path


def run_inference(req: InferenceRequest):
    job = fetch_job(req.job_id, req.organization_id)
    if job["model_id"] != req.model_id:
        raise RuntimeError("Inference job and model do not match")
    model = fetch_model(req.model_id, req.organization_id)
    if model["organization_id"] != req.organization_id:
        raise RuntimeError("Organization scope mismatch")

    started = time.perf_counter()
    update_job(req.organization_id, req.job_id, "running", started=True)
    try:
        if model["framework"] != "onnx":
            raise RuntimeError("This runtime currently supports ONNX models")
        model_path = resolve_model_path(model["model_path"])
        options = ort.SessionOptions()
        options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        options.intra_op_num_threads = max(1, os.cpu_count() or 1)
        providers = ort.get_available_providers()
        provider_order = [p for p in ("CUDAExecutionProvider", "CPUExecutionProvider") if p in providers]
        if not provider_order:
            provider_order = ["CPUExecutionProvider"]
        session = ort.InferenceSession(str(model_path), options, providers=provider_order)
        input_name = session.get_inputs()[0].name
        data = req.input_data.get("data")
        if data is None:
            raise RuntimeError("input_data.data is required")
        array = np.asarray(data, dtype=np.float32)
        if model.get("input_shape"):
            array = array.reshape(tuple(model["input_shape"]))
        outputs = session.run(None, {input_name: array})
        latency_ms = (time.perf_counter() - started) * 1000.0
        update_job(req.organization_id, req.job_id, "completed", output={"output": outputs[0].tolist()}, latency_ms=latency_ms, completed=True)
    except Exception as exc:
        try:
            update_job(req.organization_id, req.job_id, "failed", output={"error": "inference_failed"}, latency_ms=(time.perf_counter() - started) * 1000.0, completed=True)
        finally:
            raise exc


@app.get("/health")
def health():
    return {"status": "ok", "service": "pulse-core", "providers": ort.get_available_providers()}


@app.post("/infer")
def infer(req: InferenceRequest, _service: str = Depends(require_service)):
    try:
        run_inference(req)
    except RuntimeError as exc:
        raise HTTPException(status_code=409, detail="Inference request rejected") from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail="Inference execution failed") from exc
    return {"status": "completed", "job_id": req.job_id, "organization_id": req.organization_id}
