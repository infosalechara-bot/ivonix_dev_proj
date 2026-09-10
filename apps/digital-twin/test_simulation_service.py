import importlib
import sys


def load_module(monkeypatch):
    monkeypatch.setenv("SUPABASE_URL", "https://example.supabase.co")
    monkeypatch.setenv("SUPABASE_SERVICE_ROLE_KEY", "service-role")
    monkeypatch.setenv("PULSE_TWIN_SERVICE_SECRET", "twin-secret")
    sys.modules.pop("simulation_service", None)
    return importlib.import_module("simulation_service")


def test_service_auth_rejects_missing_credentials(monkeypatch):
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

    credentials = HTTPAuthorizationCredentials(scheme="Bearer", credentials="twin-secret")
    assert module.require_service(credentials) == "pulse-digital-twin"


def test_simulation_request_requires_organization_scope(monkeypatch):
    module = load_module(monkeypatch)
    request = module.SimulationRequest(organization_id="org-a", twin_id="twin-a", run_id="run-a")
    assert request.organization_id == "org-a"


def test_simulation_capacity_is_bounded(monkeypatch):
    monkeypatch.setenv("PULSE_TWIN_MAX_WORKERS", "4")
    monkeypatch.setenv("PULSE_TWIN_MAX_QUEUED", "6")
    module = load_module(monkeypatch)
    assert module.MAX_WORKERS == 4
    assert module.MAX_QUEUED == 6


def test_simulation_input_limit_is_bounded(monkeypatch):
    monkeypatch.setenv("PULSE_TWIN_MAX_INPUT_BYTES", "999999999")
    module = load_module(monkeypatch)
    assert module.MAX_INPUT_BYTES == 1024 * 1024


def test_thermal_simulation_is_deterministic(monkeypatch):
    module = load_module(monkeypatch)
    result = module.simulate_thermal({"ambient_temp": 20, "cooling_coefficient": 0.1}, {"current_temp": 30, "heat_source": 5})
    assert result == {"temperature": 34.0, "ambient_temperature": 20.0}
