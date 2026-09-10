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


def test_inference_rejects_job_model_mismatch(monkeypatch):
    module = load_module(monkeypatch)
    monkeypatch.setattr(module, "fetch_job", lambda job_id, org: {"id": job_id, "organization_id": org, "model_id": "model-a"})

    request = module.InferenceRequest(organization_id="org-a", job_id="job-a", model_id="model-b", input_data={"data": [1.0]})
    try:
        module.run_inference(request)
        assert False, "job/model mismatch must fail"
    except RuntimeError as exc:
        assert "do not match" in str(exc)


def test_model_path_cannot_escape_root(tmp_path, monkeypatch):
    module = load_module(monkeypatch)
    monkeypatch.setattr(module, "MODEL_ROOT", tmp_path.resolve())
    try:
        module.resolve_model_path("../outside.onnx")
        assert False, "path traversal must fail"
    except RuntimeError as exc:
        assert "escapes" in str(exc)
