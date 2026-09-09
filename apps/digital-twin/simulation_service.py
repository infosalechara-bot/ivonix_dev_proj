from __future__ import annotations

import os
from datetime import datetime, timezone
from typing import Any

import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="PULSE Digital Twin Simulation Engine", version="1.0")
SUPABASE_URL = os.environ.get("SUPABASE_URL", "").rstrip("/")
SERVICE_KEY = os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")

class SimulationRequest(BaseModel):
    twin_id: str
    run_id: str
    input_data: dict[str, Any] = Field(default_factory=dict)

def headers() -> dict[str, str]:
    if not SUPABASE_URL or not SERVICE_KEY:
        raise RuntimeError("Supabase worker configuration is incomplete")
    return {"apikey": SERVICE_KEY, "Authorization": f"Bearer {SERVICE_KEY}", "Content-Type": "application/json"}

def get(path: str, params: dict[str, str]) -> Any:
    r = requests.get(f"{SUPABASE_URL}/rest/v1/{path}", headers=headers(), params=params, timeout=15)
    r.raise_for_status()
    return r.json()

def patch_run(run_id: str, status: str, output: dict[str, Any] | None = None) -> None:
    data: dict[str, Any] = {"status": status}
    if output is not None:
        data["output_data"] = output
        data["completed_at"] = datetime.now(timezone.utc).isoformat()
    r = requests.patch(f"{SUPABASE_URL}/rest/v1/simulation_runs", headers=headers(), params={"id": f"eq.{run_id}"}, json=data, timeout=15)
    r.raise_for_status()

def insert_prediction(twin_id: str, device_id: str, failure_type: str, probability: float, horizon: str, action: str) -> None:
    r = requests.post(f"{SUPABASE_URL}/rest/v1/predicted_failures", headers={**headers(), "Prefer": "return=minimal"}, json={
        "twin_id": twin_id, "device_id": device_id, "failure_type": failure_type,
        "probability": probability, "time_horizon": horizon, "recommended_action": action,
    }, timeout=15)
    r.raise_for_status()

def insert_snapshot(twin_id: str, state: dict[str, Any]) -> None:
    r = requests.post(f"{SUPABASE_URL}/rest/v1/twin_snapshots", headers={**headers(), "Prefer": "return=minimal"}, json={"twin_id": twin_id, "state": state}, timeout=15)
    r.raise_for_status()

def simulate_thermal(params: dict[str, Any], inputs: dict[str, Any]) -> dict[str, Any]:
    current = float(inputs.get("current_temp", params.get("initial_temp", 25)))
    ambient = float(params.get("ambient_temp", 20))
    heat = float(inputs.get("heat_source", params.get("heat_source", 5)))
    k = float(params.get("cooling_coefficient", 0.1))
    next_temp = current + k * (ambient - current) + heat
    return {"temperature": round(next_temp, 4), "ambient_temperature": ambient}

def simulate_vibration(params: dict[str, Any], inputs: dict[str, Any]) -> dict[str, Any]:
    rms = float(inputs.get("vibration_rms", 0.5))
    speed = float(inputs.get("speed_rpm", params.get("nominal_speed_rpm", 1000)))
    nominal = float(params.get("nominal_speed_rpm", 1000))
    sensitivity = float(params.get("speed_sensitivity", 0.0001))
    next_rms = max(0.0, rms * (1 + (speed - nominal) * sensitivity))
    return {"vibration_rms": round(next_rms, 6), "speed_rpm": speed}

def simulate_energy(params: dict[str, Any], inputs: dict[str, Any]) -> dict[str, Any]:
    load = float(inputs.get("load_kw", params.get("load_kw", 10)))
    efficiency = max(0.01, min(1.0, float(params.get("efficiency", 0.9))))
    return {"load_kw": load, "estimated_input_kw": round(load / efficiency, 4), "efficiency": efficiency}

def run(req: SimulationRequest) -> None:
    try:
        rows = get("digital_twins", {"id": f"eq.{req.twin_id}", "select": "id,device_id,simulation_model,parameters"})
        if not rows:
            raise ValueError("Digital twin not found")
        twin = rows[0]
        params = twin.get("parameters") or {}
        model = twin.get("simulation_model")
        if model == "thermal": output = simulate_thermal(params, req.input_data)
        elif model == "vibration": output = simulate_vibration(params, req.input_data)
        elif model == "energy": output = simulate_energy(params, req.input_data)
        else: raise ValueError("Unsupported simulation model")

        insert_snapshot(req.twin_id, output)
        if output.get("temperature", 0) >= float(params.get("failure_temperature", 90)):
            insert_prediction(req.twin_id, twin["device_id"], "overheating", 0.9, "1 hour", "Inspect cooling system and reduce thermal load")
        if output.get("vibration_rms", 0) >= float(params.get("failure_vibration_rms", 1.5)):
            insert_prediction(req.twin_id, twin["device_id"], "excessive_vibration", 0.85, "24 hours", "Inspect bearings, alignment and mounting")
        patch_run(req.run_id, "completed", output)
    except Exception as exc:
        try:
            patch_run(req.run_id, "failed", {"error": str(exc)[:500]})
        except Exception:
            pass

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "pulse-digital-twin"}

@app.post("/simulate")
def simulate(req: SimulationRequest) -> dict[str, str]:
    try:
        patch_run(req.run_id, "running")
        # Runs in the worker process; the HTTP call returns only after dispatch.
        import threading
        threading.Thread(target=run, args=(req,), daemon=True).start()
        return {"status": "started", "run_id": req.run_id}
    except Exception as exc:
        raise HTTPException(status_code=500, detail="Simulation worker unavailable") from exc
