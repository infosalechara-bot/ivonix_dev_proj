import importlib
import os
from pathlib import Path

os.environ.setdefault("SUPABASE_URL", "https://example.supabase.co")
os.environ.setdefault("SUPABASE_SERVICE_ROLE_KEY", "test-service-role-key")
os.environ.setdefault("PULSE_AI_TRAINING_SERVICE_SECRET", "test-training-secret")
os.environ.setdefault("MODEL_ROOT", "/tmp/pulse-test-models")

training_service = importlib.import_module("training_service")
from fastapi.security import HTTPAuthorizationCredentials
from fastapi import HTTPException


def test_require_service_accepts_exact_bearer_secret():
    credentials = HTTPAuthorizationCredentials(
        scheme="Bearer", credentials="test-training-secret"
    )
    assert training_service.require_service(credentials) is True


def test_require_service_rejects_wrong_secret():
    credentials = HTTPAuthorizationCredentials(scheme="Bearer", credentials="wrong")
    try:
        training_service.require_service(credentials)
    except HTTPException as exc:
        assert exc.status_code == 401
    else:
        raise AssertionError("invalid service credential was accepted")


def test_require_service_rejects_missing_credentials():
    try:
        training_service.require_service(None)
    except HTTPException as exc:
        assert exc.status_code == 401
    else:
        raise AssertionError("missing service credential was accepted")


def test_safe_artifact_is_confined_to_model_root():
    path = training_service.safe_artifact("model-123")
    root = Path(os.environ["MODEL_ROOT"]).resolve()
    assert root in path.parents
    assert path.name == "model.joblib"


def test_safe_artifact_rejects_path_escape():
    try:
        training_service.safe_artifact("../../escape")
    except ValueError as exc:
        assert str(exc) == "Artifact path escapes model root"
    else:
        raise AssertionError("path escape was accepted")
