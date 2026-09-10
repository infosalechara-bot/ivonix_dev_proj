# PULSE production hardening status

This is a living gate record. A capability is **implemented** only when code exists; it is **verified** only when the corresponding test or runtime evidence exists. Production certification requires all gates below to be verified.

## Current branch

- Branch: `pulse-production-bulk-hardening`
- PR: #6
- Current head: `f7784bd78ee85447256898762cc7989bbafb748a`
- Production migrations: not applied by this hardening pass.

## Gates

| Gate | Status | Evidence / remaining work |
|---|---|---|
| Original P0/P1 audit closure | IN PROGRESS | Foundation changes exist, but every original finding still needs explicit regression evidence. |
| Device/event contracts | IN PROGRESS | Versioned contracts and Event Bus tests exist. Device ingress now has an explicit security boundary: human JWT authentication is not applied to `/api/v1/device-gateway/**`; the gateway itself requires a bearer device credential and binds identity/tenant from `key_devices`. Full cross-service contract execution remains. |
| Authentication | VERIFIED-PARTIAL | Security API endpoints reject missing/invalid bearer tokens; CI typecheck and regression tests pass. Full endpoint/worker negative-test sweep remains. Device ingress uses separate device-credential authentication in `DeviceGatewayService`. |
| Authorization | VERIFIED-PARTIAL | Security API verifies organization membership and device ownership before tenant-scoped writes/anomaly processing. Ontology services perform resource/org checks in multiple critical paths; full resource/action matrix remains. |
| Tenant isolation | VERIFIED-PARTIAL | Security API queries are organization-scoped; device ingestion derives organization from the authenticated device record and never accepts caller-selected tenant identity. Full cross-tenant negative integration suite remains. |
| Privacy / DSAR | IMPLEMENTED, VERIFY | PRV-056 implementation and migration staged; end-to-end DSAR/erasure/legal-hold verification remains. |
| Trust / anti-fraud | IMPLEMENTED, VERIFY | TRU-057 implementation exists; production signal/rule and false-positive testing remains. |
| FinOps | IMPLEMENTED, VERIFY | FIN-058 implementation exists; billing integration and anomaly validation remain. |
| Runbook | IMPLEMENTED, VERIFY | RNB-059 implementation exists; OBSERVE-to-Runbook alert integration remains. |
| Accessibility | AUTOMATED | WCAG AA audit script is part of Web CI; current run must pass before certification. |
| API build/test coverage | VERIFIED | API CI run `34436056243` completed successfully: TypeScript typecheck and security-boundary tests passed. |
| Security / OWASP API | NOT CERTIFIED | Requires full executable API security/negative tests and review beyond the current auth boundary. |
| Integration / E2E | NOT CERTIFIED | Complete machine registration → credential auth → telemetry → Event Bus → ontology/intelligence → command → acknowledgement → audit/ledger flow remains. |
| Failure / chaos | NOT CERTIFIED | Service, database, Event Bus, network and recovery failure scenarios remain. |
| Load | NOT CERTIFIED | Required targets: 10k msg/s for 5 min, p95 <250 ms, p99 <800 ms; 500 concurrent API reads p95 <300 ms; errors <1%. |
| Backup / restore | NOT CERTIFIED | RTO ≤1h and RPO ≤5m must be demonstrated. |
| Deployment | BLOCKED/PENDING | Vercel has reported the account deployment-rate limit; deployment evidence must be obtained after the limit clears or an approved deployment path is used. |
| Runtime verification | NOT CERTIFIED | Requires deployed smoke tests and production-like runtime evidence. |

## Recent hardening change

The Spring Security boundary previously required a human JWT for every non-public endpoint. That conflicted with the device-fabric design because `/api/v1/device-gateway/messages` is authenticated by a device credential stored against `key_devices`, not by a human session. The security chain now explicitly permits only the device-gateway path through the human-authentication layer; `DeviceGatewayController` still requires a bearer credential, and `DeviceGatewayService` verifies the credential, device identity, tenant binding, credential expiry/status, sequence monotonicity, and event idempotency before accepting telemetry.

This is an implementation correction, not a production-certification claim. CI evidence for the new commit is still pending.

## Database advisory

The current Supabase security advisor reports one warning: the `vector` extension is installed in `public`. The current database has one `public.find_embeddings.embedding` column using the `public.vector` type. This should be remediated in a controlled migration after checking all vector operators/functions and search paths; it is not being changed blindly in production.

## Certification rule

Do not label PULSE production-ready until all NOT CERTIFIED gates have executable evidence and all BLOCKED items have an approved resolution. Green CI alone is insufficient.
