import importlib


def load_module(monkeypatch):
    monkeypatch.setenv("SUPABASE_URL", "https://example.supabase.co")
    monkeypatch.setenv("SUPABASE_SERVICE_ROLE_KEY", "service-role")
    monkeypatch.setenv("PULSE_CORE_SERVICE_SECRET", "core-secret")
    return importlib.import_module("core_runtime")


def test_service_auth_rejects_missing_or_wrong_credentials(monkeypatch):
    module = load_module(monkeypatch)
    from fastapi import HTTPException

    try:
        module.require_service(None)
        assert False, "missing credentials must fail"
    except HTTPException as exc:
        assert exc.status_code == 401


def test_service_auth_accepts_exact_bearer(monkeypatch):
    module = load_module(monkeypatch)
    from fastapi.security import HTTPAuthorizationCredentials

    credentials = HTTPAuthorizationCredentials(scheme="Bearer", credentials="core-secret")
    assert module.require_service(credentials) == "pulse-core"


def test_claim_is_authoritative_and_request_payload_is_not_used(monkeypatch):
    module = load_module(monkeypatch)
    monkeypatch.setattr(module, "claim_job", lambda job_id, org: {
        "id": job_id,
        "organization_id": org,
        "model_id": "model-a",
        "payload": {"data": [2.0]},
    })
    monkeypatch.setattr(module, "fetch_model", lambda model_id, org: {
        "id": model_id,
        "organization_id": org,
        "model_path": "model.onnx",
        "input_shape": None,
        "output_shape": None,
        "framework": "invalid",
    })
    monkeypatch.setattr(module, "fail_job", lambda *args: True)

    request = module.InferenceRequest(
        organization_id="org-a",
        job_id="job-a",
        model_id="model-a",
        input_data={"data": [999.0]},
    )
    try:
        module.run_inference(request)
        assert False, "invalid model framework must fail"
    except RuntimeError as exc:
        assert "supports ONNX" in str(exc)


def test_inference_rejects_job_model_mismatch_before_execution(monkeypatch):
    module = load_module(monkeypatch)
    monkeypatch.setattr(module, "claim_job", lambda job_id, org: {
        "id": job_id,
        "organization_id": org,
        "model_id": "model-a",
        "payload": {"data": [1.0]},
    })
    failed = []
    monkeypatch.setattr(module, "fail_job", lambda *args: failed.append(args) or True)

    request = module.InferenceRequest(organization_id="org-a", job_id="job-a", model_id="model-b", input_data={"data": [1.0]})
    try:
        module.run_inference(request)
        assert False, "job/model mismatch must fail"
    except RuntimeError as exc:
        assert "do not match" in str(exc)
    assert failed


def test_unclaimable_job_is_rejected_without_model_execution(monkeypatch):
    module = load_module(monkeypatch)
    monkeypatch.setattr(module, "claim_job", lambda job_id, org: None)

    request = module.InferenceRequest(organization_id="org-a", job_id="job-a", model_id="model-a", input_data={"data": [1.0]})
    try:
        module.run_inference(request)
        assert False, "an already claimed/terminal job must not execute"
    except RuntimeError as exc:
        assert "not claimable" in str(exc)


def test_model_path_cannot_escape_root(tmp_path, monkeypatch):
    module = load_module(monkeypatch)
    monkeypatch.setattr(module, "MODEL_ROOT", tmp_path.resolve())
    try:
        module.resolve_model_path("../outside.onnx")
        assert False, "path traversal must fail"
    except RuntimeError as exc:
        assert "escapes" in str(exc)
