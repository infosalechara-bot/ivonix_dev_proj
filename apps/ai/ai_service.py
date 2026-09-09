from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field, ConfigDict

app = FastAPI(title="PULSE AI Diagnostic Service", version="1.1")
SCHEMA_VERSION = "1.0"


class DiagnosisRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    schema_version: str | None = Field(default=SCHEMA_VERSION, alias="schemaVersion")
    machine_domain: str | None = Field(default=None, alias="deviceDomain")
    telemetry: dict[str, Any] = Field(default_factory=dict)
    evidence: list[dict[str, Any]] = Field(default_factory=list)


@app.get("/health")
def health():
    return {"status": "ok", "service": "pulse-ai", "schemaVersion": SCHEMA_VERSION}


@app.post("/diagnose")
def diagnose(req: DiagnosisRequest):
    t = req.telemetry
    base = {"schemaVersion": SCHEMA_VERSION, "engineType": "RULE_ENGINE", "modelId": "pulse-diagnostic-rules", "modelVersion": "1.0"}
    if isinstance(t.get("temperature"), (int, float)) and t["temperature"] > 85:
        return {**base, "probableFault": "Overheating", "confidence": 0.92, "recommendedActions": ["Inspect cooling system", "Verify operating load"], "doNotDisassemble": True, "evidence": ["temperature > 85"]}
    if isinstance(t.get("vibration_rms"), (int, float)) and t["vibration_rms"] > 1.5:
        return {**base, "probableFault": "Bearing degradation", "confidence": 0.85, "recommendedActions": ["Inspect bearings", "Check alignment"], "doNotDisassemble": True, "evidence": ["vibration_rms > 1.5"]}
    if req.evidence:
        return {**base, "probableFault": "Acoustic/evidence anomaly", "confidence": 0.70, "recommendedActions": ["Review supplied evidence", "Follow approved diagnostic procedure"], "doNotDisassemble": True, "evidence": ["external evidence present"]}
    return {**base, "probableFault": "No rule-based fault detected", "confidence": 0.10, "recommendedActions": ["Continue telemetry collection"], "doNotDisassemble": True, "evidence": []}
