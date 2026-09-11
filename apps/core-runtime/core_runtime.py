import hmac
import os
import threading
import time
import uuid
from pathlib import Path
from typing import Any

import numpy as np
import onnxruntime as ort
import requests
from fastapi import Depends, FastAPI, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel

app = FastAPI(title="PULSE CORE Runtime", version="0.4.0")
SUPABASE_URL = os.environ.get("SUPABASE_URL", "").rstrip("/")
SUPABASE_SERVICE_ROLE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")
CORE_SERVICE_SECRET = os.environ.get("PULSE_CORE_SERVICE_SECRET", "")
MODEL_ROOT = Path(os.environ.get("CORE_MODEL_ROOT", "/models")).resolve()
LEASE_SECONDS = max(5, min(3600, int(os.environ.get("PULSE_INFERENCE_LEASE_SECONDS", "60"))))
HEARTBEAT_SECONDS = max(2, min(LEASE_SECONDS // 2, int(os.environ.get("PULSE_INFERENCE_HEARTBEAT_SECONDS", "20"))))
bearer = HTTPBearer(auto_error=False)

if not SUPABASE_URL or not SUPABASE_SERVICE_ROLE_KEY or not CORE_SERVICE_SECRET:
    raise RuntimeError("CORE runtime requires Supabase credentials and PULSE_CORE_SERVICE_SECRET")


class InferenceRequest(BaseModel):
    organization_id: str
    job_id: str
    model_id: str
    input_data: dict[str, Any]


LEASE_OWNER = f"{os.environ.get('HOSTNAME', 'core-runtime')}:{uuid.uuid4()}"


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


def claim_job(job_id: str, organization_id: str):
    response = supabase_request(
        "POST", "rpc/claim_inference_job",
        json={"p_job_id": job_id, "p_org_id": organization_id, "p_lease_owner": LEASE_OWNER, "p_lease_seconds": LEASE_SECONDS},
    )
    rows = response.json()
    return rows[0] if rows else None


def renew_lease(job_id: str, organization_id: str) -> bool:
    response = supabase_request(
        "POST", "rpc/renew_inference_lease",
        json={"p_job_id": job_id, "p_org_id": organization_id, "p_lease_owner": LEASE_OWNER, "p_lease_seconds": LEASE_SECONDS},
    )
    return bool(response.json())


def complete_job(job_id: str, organization_id: str, result: dict[str, Any], latency_ms: float) -> bool:
    response = supabase_request(
        "POST", "rpc/complete_inference_job",
        json={"p_job_id": job_id, "p_org_id": organization_id, "p_lease_owner": LEASE_OWNER, "p_result": result, "p_latency_ms": latency_ms},
    )
    return bool(response.json())


def fail_job(job_id: str, organization_id: str, reason: str, latency_ms: float) -> bool:
    response = supabase_request(
        "POST", "rpc/fail_inference_job",
        json={"p_job_id": job_id, "p_org_id": organization_id, "p_lease_owner": LEASE_OWNER, "p_reason": reason[:500], "p_latency_ms": latency_ms},
    )
    return bool(response.json())


def heartbeat(job_id: str, organization_id: str, stop: threading.Event, lost: threading.Event) -> None:
    while not stop.wait(HEARTBEAT_SECONDS):
        try:
            if not renew_lease(job_id, organization_id):
                lost.set()
                return
        except Exception:
            lost.set()
            return


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
    claim = claim_job(req.job_id, req.organization_id)
    if claim is None:
        raise RuntimeError("Inference job is not claimable")

    model_id = str(claim["model_id"])
    if model_id != req.model_id:
        try:
            fail_job(req.job_id, req.organization_id, "job_model_mismatch", 0)
        finally:
            raise RuntimeError("Inference job and model do not match")

    model = fetch_model(model_id, req.organization_id)
    if model["organization_id"] != req.organization_id:
        fail_job(req.job_id, req.organization_id, "organization_scope_mismatch", 0)
        raise RuntimeError("Organization scope mismatch")
    if not model.get("execution_enabled", False):
        fail_job(req.job_id, req.organization_id, "model_not_admitted", 0)
        raise RuntimeError("Model is not admitted for execution")

    stop = threading.Event()
    lease_lost = threading.Event()
    heartbeat_thread = threading.Thread(
        target=heartbeat,
        args=(req.job_id, req.organization_id, stop, lease_lost),
        name=f"inference-heartbeat-{req.job_id}",
        daemon=True,
    )
    heartbeat_thread.start()

    started = time.perf_counter()
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

        data = (claim.get("payload") or {}).get("data")
        if data is None:
            raise RuntimeError("input_data.data is required")
        array = np.asarray(data, dtype=np.float32)
        if model.get("input_shape"):
            array = array.reshape(tuple(model["input_shape"]))
        outputs = session.run(None, {input_name: array})
        latency_ms = (time.perf_counter() - started) * 1000.0

        if lease_lost.is_set():
            raise RuntimeError("inference lease lost before completion")
        if not complete_job(req.job_id, req.organization_id, {"output": outputs[0].tolist()}, latency_ms):
            raise RuntimeError("inference completion rejected because the lease is no longer owned")
    except Exception as exc:
        latency_ms = (time.perf_counter() - started) * 1000.0
        try:
            fail_job(req.job_id, req.organization_id, str(exc), latency_ms)
        except Exception:
            pass
        raise
    finally:
        stop.set()
        heartbeat_thread.join(timeout=max(1, HEARTBEAT_SECONDS))


def fetch_model(model_id: str, organization_id: str):
    response = supabase_request("GET", "core_models", params={
        "id": f"eq.{model_id}", "organization_id": f"eq.{organization_id}",
        "select": "id,organization_id,model_path,input_shape,output_shape,framework,execution_enabled,admitted_at,admitted_by"
    })
    rows = response.json()
    if len(rows) != 1:
        raise RuntimeError("Model not found")
    return rows[0]


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
