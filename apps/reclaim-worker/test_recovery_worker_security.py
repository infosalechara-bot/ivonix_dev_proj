import importlib
import os

import pytest

os.environ.setdefault("SUPABASE_URL", "https://example.supabase.co")
os.environ.setdefault("SUPABASE_SERVICE_ROLE_KEY", "test-service-key")
os.environ.setdefault("PULSE_RECLAIM_WORKER_SECRET", "test-worker-secret")

worker = importlib.import_module("recovery_worker")


def request(**overrides):
    values = {"jobId": "job-1", "deviceId": "device-1", "jobType": "file", "targetPath": "/reclaim-inputs/image.dd", "targetTable": None, "targetIdentifier": None}
    values.update(overrides)
    return worker.RecoveryRequest(**values)


def job(**overrides):
    values = {"id": "job-1", "organization_id": "org-1", "device_id": "device-1", "job_type": "file", "target_path": "/reclaim-inputs/image.dd", "target_table": None, "target_identifier": None, "status": "queued"}
    values.update(overrides)
    return values


def test_worker_rejects_target_tampering():
    with pytest.raises(PermissionError):
        worker.validate_request_matches_job(request(targetPath="/reclaim-inputs/other.dd"), job())


def test_worker_rejects_job_type_tampering():
    with pytest.raises(PermissionError):
        worker.validate_request_matches_job(request(jobType="system"), job())


def test_worker_rejects_completed_job_replay():
    with pytest.raises(PermissionError):
        worker.validate_request_matches_job(request(), job(status="completed"))


def test_worker_accepts_exact_queued_job():
    assert worker.validate_request_matches_job(request(), job())["organization_id"] == "org-1"
