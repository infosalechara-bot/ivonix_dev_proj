# PULSE Device Protocol v1

## Boundary

`PHYSICAL MACHINE -> device agent/firmware -> authenticated transport -> edge gateway -> PULSE`

The device boundary is transport-neutral. Adapters may use MQTT, HTTPS, CAN-derived gateways, OPC-UA, ROS, Modbus, ARINC, NMEA, or a vendor protocol, but every adapter MUST normalize into the versioned PULSE message contracts.

## Identity

Every device has a stable device identifier and a credential/key identifier. Enrollment is explicit; credentials are rotated and revocable. A device MUST NOT be trusted because it supplied an arbitrary organization identifier.

## Message requirements

Every message carries:

- schema version
- unique message ID
- device ID
- message type
- source timestamp
- monotonic sequence number
- payload
- signature/key ID when the transport does not already provide equivalent authenticated integrity

The gateway verifies device identity, organization ownership, signature/integrity, timestamp bounds, sequence/replay rules, and schema version before persistence or event publication.

## Telemetry

Telemetry is normalized into `telemetry_envelope_v1.json`. Unknown vendor fields may be retained under metadata, but canonical measurements MUST NOT be overloaded with vendor-specific semantics.

## Commands

Commands use `command_lifecycle_v1.json`. A command is never considered successful merely because it was queued. The authoritative lifecycle is REQUESTED -> AUTHORIZED -> DISPATCHED -> ACKNOWLEDGED -> SUCCEEDED/FAILED, with EXPIRED and CANCELLED terminal states.

Commands require an idempotency key and expiry. Edge retry MUST be safe to repeat without executing the physical action twice.

## Offline operation

Edge nodes queue signed messages durably. Reconnect processing is idempotent. Sequence gaps are observable; duplicate message IDs are ignored after integrity validation; stale commands are expired rather than replayed. Device clock skew is recorded and bounded rather than silently rewriting event time.

## Safety

Physical actuation is policy-gated. High-impact commands require authorization and capability checks before dispatch. Diagnostic recommendations are not equivalent to actuation authorization.
