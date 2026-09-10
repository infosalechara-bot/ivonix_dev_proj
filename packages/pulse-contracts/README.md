# PULSE Contracts

Versioned, implementation-neutral contracts shared by Java services, Python workers, edge components, and future SDKs.

## Block 1 canonical hierarchy

`packages/pulse-contracts` is the single shared contract boundary. The canonical Block 1 schemas are under `schemas/`; existing machine/domain contracts remain distinct payload contracts.

- **API** — `schemas/pulse_error_v1.json`, `schemas/pulse_pagination_v1.json`, plus `X-Request-ID`.
- **Identity** — `schemas/pulse_claims_v1.json` for the additive JWT claim contract.
- **Event Bus** — `schemas/pulse_event_v1.json`, the universal transport envelope around domain payloads.
- **Machine protocol** — existing telemetry, device-message, and command-lifecycle contracts remain unchanged.
- **Domain** — existing diagnostic-result, provenance, and observability contracts remain unchanged.
- **Registry** — `public.event_types` is the authoritative event type/version registry. No `reliability_schemas`, outbox, inbox, or DLQ tables are introduced by Block 1.

## v1 invariants

- Event IDs use ULID representation.
- Entity identifiers remain owned by their domains; public opaque identifiers may use NanoID where applicable.
- Integration timestamps are UTC and exactly `YYYY-MM-DDTHH:mm:ss.SSSZ` at the boundary.
- API paths are rooted at `/api/v1`.
- `X-Request-ID` is accepted/generated at ingress and propagated through downstream calls and event correlation.
- Producers and consumers validate the declared schema version and fail closed on unsupported versions.
- Event payload schemas describe `payload`; the universal envelope is validated separately.
- Event signatures are required at the contract boundary; cryptographic algorithm and key-management policy remain implementation concerns.
- JWTs preserve `organization_id` and carry issuer `pulse` and audience `pulse-api` in the target contract; authorization still requires server-side tenant/resource checks.
- Target event compatibility policy is N-1 for 90 days, represented explicitly by registry metadata.

## Registry contract

The live `public.event_types` registry contains the ten Block 1 event types:

`credential_issued`, `credential_revoked`, `device_telemetry_received`, `diagnostic_completed`, `founder_decision`, `inference_completed`, `key_device_activated`, `key_device_revoked`, `ledger_transaction`, `security_event`.

The registry is extended with `event_version`, `schema_id`, `schema_status`, `deprecated_at`, `compatibility_until`, `envelope_version`, `compatibility`, and `successor`. `payload_schema` is the JSON Schema for the payload, not for the universal envelope. `(name,event_version)` and `schema_id` are unique registry identities.

Each active event version must have exactly one fixture at `fixtures/v1/payloads/<event_name>.v1.golden.json` and must wrap successfully in `PulseEventV1`.

## Verification

Run from repository root:

    ./scripts/verify_block1.sh

The verification surface is deliberately multi-language: TypeScript, Python, and Java validate the same canonical schemas and golden fixtures. Registry coverage is a separate fail-closed check against a non-production Supabase environment.

Missing registry credentials are a failure, not a skip. Production credentials must never be used by CI.

## Compatibility

JWT migration is additive: `organization_id` is retained permanently while `org_id`, `role`, `scopes`, `actor_type`, `session_id`, `iss`, and `aud` are introduced. Consumers may fall back to legacy role-based authorization during the transition; service adoption is outside Block 1.

Error migration is additive: the canonical `error` object coexists with legacy top-level compatibility keys during the agreed 90-day window. Removal requires an explicit release/deprecation decision; Block 1 does not remove legacy keys.

## Block 1 boundary

Block 1 defines and verifies the contract surface. It does **not** migrate `pulse-core`, `pulse-observe`, JWT issuance code, or other application services. The first service adoption is specified separately in the root `MIGRATION.md` and begins only after contract verification is green.
