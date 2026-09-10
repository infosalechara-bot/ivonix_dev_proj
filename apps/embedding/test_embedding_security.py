import os
os.environ.setdefault('PULSE_EMBEDDING_SERVICE_SECRET','test-secret')

from fastapi import HTTPException
from embedding_service import _safe_host, download


def test_private_host_is_rejected():
    try:
        _safe_host('127.0.0.1')
    except HTTPException as exc:
        assert exc.status_code == 400
    else:
        raise AssertionError('private address must be rejected')


def test_non_https_is_rejected():
    try:
        download('http://example.com/file')
    except HTTPException as exc:
        assert exc.status_code == 400
    else:
        raise AssertionError('non-HTTPS URL must be rejected')
