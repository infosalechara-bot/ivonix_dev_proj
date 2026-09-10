# Pulse Event Envelope v1

Universal transport envelope. `event_version` identifies the payload contract; `envelope_version` identifies this transport contract. Existing machine/domain contracts remain payload contracts and are not flattened.

Invariants: event IDs are ULIDs; event types are dot-namespaced; versions are >=1; integration timestamps are exactly `YYYY-MM-DDTHH:mm:ss.SSSZ`; correlation is mandatory; signatures are mandatory. Algorithm/key-management policy is implementation-specific.
