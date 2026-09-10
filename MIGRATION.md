# Block 1 Contract Migration

Block 1 freezes and verifies the shared PULSE integration contract. It does not migrate application services.

The universal `PulseEvent<T>` envelope is transport metadata; existing telemetry, device-message, command-lifecycle, diagnostic, provenance, and observability contracts remain payload contracts. API errors retain legacy compatibility fields for one 90-day transition window. `organization_id` remains in JWTs permanently. Outbox/inbox/DLQ persistence is outside Block 1.

After verification is green, the first adoption surface is `pulse-core` producer -> `pulse-observe` consumer in staging. Producer first, dual path, compare for seven days, then cut over. A missing verification credential is a failure, never a pass.
