# Block 1 — Golden Path Certification Manifest

This manifest is the certification boundary for Golden Path 1.

## Required runtime proof

The live certification run must prove all 13 requirements:

1. Device registration creates the device row and credential.
2. Device credential authenticates to EMQX.
3. Telemetry is received over MQTT.
4. MQTT webhook reaches and validates the backend.
5. Telemetry is persisted with the expected device data.
6. Device becomes online and `last_seen` advances.
7. A telemetry event is emitted and persisted.
8. The dashboard exposes the resulting live telemetry through its live-device projection.
9. A command is accepted with HTTP 202 and persisted.
10. The device receives the command on its authorized MQTT subscription.
11. The device ACK reaches the backend and the command becomes acknowledged.
12. Registration, telemetry, command issuance, and command acknowledgement are auditable.
13. The entire sequence is reproducible by the checked-in harness and CI workflow.

## Certification rule

Static compilation, contract verification, Compose validation, or Vercel deployment success is not equivalent to live certification. `BLOCK-1 VERIFIED` may only be asserted after a successful live Golden Path execution and the required observation/canary evidence.

## Current limitation

The checked-in harness currently verifies the persisted event row and live API projection. It does not independently verify the canonical Ed25519 `PulseEvent<T>` signature/envelope or browser DOM subscription behavior. Those are therefore not claimed as independently proven by this manifest.
