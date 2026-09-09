# PULSE Contracts

Versioned, implementation-neutral contracts shared by Java services, Python workers, edge components, and future SDKs.

## v1 contracts

- `service_identity_v1.json` — internal service identity and authorization envelope.
- `diagnostic_result_v1.json` — canonical engineering diagnostic result.
- `telemetry_envelope_v1.json` — canonical machine telemetry envelope.

Contracts are compatibility boundaries. Producers and consumers must validate the declared schema version and fail closed on unsupported versions.
