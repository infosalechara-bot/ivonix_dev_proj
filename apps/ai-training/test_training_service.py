import importlib
import os
from pathlib import Path

os.environ.setdefault("SUPABASE_URL", "https://example.supabase.co")
os.environ.setdefault("SUPABASE_SERVICE_ROLE_KEY", "test-service-role-key")
os.environ.setdefault("PULSE_AI_TRAINING_SERVICE_SECRET", "test-training-secret")
os.environ.setdefault("MODEL_ROOT", "/tmp/pulse-test-models")
os.environ.setdefault("DATASET_ROOT", "/tmp/pulse-test-datasets")
os.environ.setdefault("MAX_DATASET_BYTES", "1024")

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


def test_safe_dataset_is_confined_and_size_checked(tmp_path):
    root = tmp_path / "datasets"
    root.mkdir()
    source = root / "tenant.csv"
    source.write_text("target,value\n1,2\n")
    training_service.DATASET_ROOT = root.resolve()
    training_service.MAX_DATASET_BYTES = 1024
    resolved = training_service.safe_dataset(str(source))
    assert resolved == source.resolve()


def test_safe_dataset_rejects_path_escape(tmp_path):
    root = tmp_path / "datasets"
    root.mkdir()
    outside = tmp_path / "outside.csv"
    outside.write_text("target,value\n1,2\n")
    training_service.DATASET_ROOT = root.resolve()
    try:
        training_service.safe_dataset(str(outside))
    except ValueError as exc:
        assert str(exc) == "Dataset path escapes dataset root"
    else:
        raise AssertionError("dataset path escape was accepted")


def test_safe_dataset_rejects_remote_uri(tmp_path):
    training_service.DATASET_ROOT = tmp_path.resolve()
    for uri in ("https://example.invalid/data.csv", "s3://bucket/data.csv"):
        try:
            training_service.safe_dataset(uri)
        except ValueError as exc:
            assert str(exc) == "Dataset must use a local managed dataset path"
        else:
            raise AssertionError("remote dataset URI was accepted")


def test_safe_dataset_rejects_oversized_file(tmp_path):
    root = tmp_path / "datasets"
    root.mkdir()
    source = root / "large.csv"
    source.write_bytes(b"x" * 1025)
    training_service.DATASET_ROOT = root.resolve()
    training_service.MAX_DATASET_BYTES = 1024
    try:
        training_service.safe_dataset(str(source))
    except ValueError as exc:
        assert str(exc) == "Dataset exceeds configured size limit"
    else:
        raise AssertionError("oversized dataset was accepted")
