# Gate 2 — Internal Service Boundary

All privileged worker endpoints must have two independent controls:

1. **Service authentication** — a worker-specific bearer secret is required; missing configuration fails startup.
2. **Resource authorization** — every request carries an organization scope and the worker resolves the requested resource inside that scope before performing work.

CORE additionally verifies `inference_job.model_id == request.model_id` and constrains model files to the configured read-only model root.

Digital Twin additionally verifies that the simulation run belongs to the requested twin and that the twin belongs to the supplied organization.

Health endpoints remain unauthenticated and expose only service health/provider metadata. Operational endpoints must never return raw internal exception text.

Secrets required by this gate:

- `PULSE_CORE_SERVICE_SECRET`
- `PULSE_TWIN_SERVICE_SECRET`

These are service credentials, not user credentials. Rotation should be handled by the deployment secret manager; the values must never be committed.
