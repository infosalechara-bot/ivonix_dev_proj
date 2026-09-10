# PULSE Contracts

Versioned, implementation-neutral contracts shared by Java services, Python workers, edge components, and future SDKs.

## Block 1 canonical hierarchy

`packages/pulse-contracts` is the single shared contract boundary. Do not create a second competing contract package or event-schema registry.

- **API contracts** — `pulse_error_v1.json`, `pulse_pagination_v1.json`, and `X-Request-ID` propagation.
- **Identity contract** — `pulse_claims_v1.json` for the canonical application JWT claim set.
- **Event Bus contract** — `pulse_event_v1.json`, the universal transport envelope around domain payloads.
- **Machine protocol contracts** — telemetry, device messages, and command lifecycle remain protocol-specific and are not flattened into the event envelope.
- **Domain contracts** — diagnostic result, provenance, and observability remain domain-specific payload contracts.
- **Schema registry** — `public.event_types` is the authoritative database registry for event type + version + payload schema. The migration extends that existing table; no `reliability_schemas` table is introduced.

## v1 invariants

- Event IDs use ULID representation.
- Entity identifiers remain UUID v7 where the owning domain supports UUID identifiers; public opaque identifiers may use NanoID.
- Integration timestamps are UTC and exactly `YYYY-MM-DDTHH:mm:ss.SSSZ` at the boundary.
- API paths are rooted at `/api/v1`.
- `X-Request-ID` is accepted/generated at ingress and propagated through downstream calls and event correlation.
- Producers and consumers validate the declared schema version and fail closed on unsupported versions.
- Event payload schemas describe `payload`; the universal envelope is validated separately.
- Event signatures are required at the contract boundary; cryptographic algorithm/key-management policy is an implementation concern and must not be inferred from the field alone.
- JWTs must carry issuer `pulse` and audience `pulse-api`; authorization still requires server-side tenant/resource checks.
- Target event compatibility policy is N-1 for 90 days, subject to an explicit registry `compatibility_until` value.

## Existing contracts retained

Existing machine/device/domain contracts remain authoritative for their respective protocols. For example, telemetry already declares `schemaVersion`, `eventId`, `organizationId`, `deviceId`, `timestamp`, and `measurements`; it is therefore a payload/domain contract, not a replacement for the universal event envelope.

Contracts are compatibility boundaries. Producers and consumers must validate the declared schema version and fail closed on unsupported versions.
