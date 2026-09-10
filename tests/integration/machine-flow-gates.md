# PULSE machine-flow integration gates

This is the required executable test sequence for production certification. Each gate must produce a timestamped result and correlation ID; a green unit test does not satisfy an integration gate.

1. **Registration** — create device under organization A; assert organization B cannot address it.
2. **Credential issuance** — issue/rotate device credential; assert expired/revoked credentials fail closed.
3. **Telemetry ingress** — submit a versioned telemetry event; assert tenant/device binding and idempotency.
4. **Event Bus** — assert one logical event produces one durable event and each delivery is claimed by only one worker.
5. **Ontology** — resolve machine/device identity from the same tenant context.
6. **Intelligence** — produce diagnosis/observation with model/version provenance.
7. **Command authorization** — issue a command only from an authorized principal; reject cross-tenant and insufficient-role attempts.
8. **Acknowledgement** — correlate acknowledgement to the original command/device/event.
9. **Audit** — assert security-sensitive actions are present in the ledger/audit trail.
10. **Failure path** — kill/restart the consumer between claim and acknowledgement; assert lease expiry/retry without permanent loss or uncontrolled duplication.
11. **Concurrent path** — run at least two consumers against the same pending workload; assert one ownership claim per delivery.
12. **Storage path** — assert signed/private object access is tenant-scoped and cross-tenant object reads fail.
13. **Recovery** — invoke RECLAIM against a persisted job; mutate request targets and assert rejection; replay a completed job and assert rejection.
14. **Founder gate** — exercise a critical action with valid, expired, reused and mismatched approval; assert only the valid single-use approval succeeds.
15. **Runtime smoke** — execute the complete chain against the deployed environment and persist evidence.

## Certification thresholds

- 10,000 messages/s sustained for 5 minutes.
- API telemetry p95 <250 ms and p99 <800 ms.
- 500 concurrent API reads p95 <300 ms.
- Error rate <1%.
- No cross-tenant data exposure.
- No unbounded retry loop.
- No duplicate side effect attributable to a delivery race.
- RTO <=1 hour and RPO <=5 minutes demonstrated by restore evidence.
