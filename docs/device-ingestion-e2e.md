# PULSE device ingestion E2E contract

## Enrollment
1. A human organization member creates a bounded activation code through PULSE KEY.
2. The machine activates the code and receives a device UUID plus a short-lived-enough-to-rotate credential.
3. The credential is stored only as a SHA-256 hash in `key_devices.credential_hash`.
4. The machine never chooses its organization; organization ownership comes from the activation record.

## Ingestion
POST `/api/v1/device-gateway/messages`

Headers:
- `Authorization: Bearer <device credential>`
- optional `X-PULSE-Device-ID: <device UUID>`

Body follows `packages/pulse-contracts/device_message_v1.json`.

The gateway:
- authenticates the bearer credential;
- derives the organization from the credential record;
- requires the body device ID to equal the authenticated device ID;
- accepts only supported schema/message types;
- atomically advances the device sequence and rejects replay/out-of-order messages;
- stores the message as an organization-scoped event;
- uses `(organization_id, messageId)` as an idempotency key;
- returns the existing event for an exact duplicate.

## Important boundary
The gateway currently provides authenticated credential + sequence integrity. Cryptographic message signatures using device public keys are a separate capability and must not be implied by the `publicKey` field in activation records until verification is implemented end-to-end.
