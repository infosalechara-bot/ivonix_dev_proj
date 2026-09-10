# PULSE production hardening status

This is a living gate record. A capability is **implemented** only when code exists; it is **verified** only when the corresponding test or runtime evidence exists. Production certification requires all gates below to be verified.

## Current branch

- Branch: `pulse-production-bulk-hardening`
- PR: #6
- Production migrations: not applied by this hardening pass.

## Whole-platform reconciliation

The audit covers repository code, frontend, Java backend, Python services/workers, Supabase schema/RLS/storage, migrations, Docker Compose, CI/CD and Vercel deployment.

- Live Supabase project is `ACTIVE_HEALTHY` on PostgreSQL 17.6 in `ap-southeast-2`.
- Live database currently has 84 public tables, all 84 with RLS enabled, 124 public policies, 0 storage buckets and 0 storage policies.
- The repository contains staged migrations for additional Academy/Global/Meet and Privacy/Trust/FinOps/Runbook schemas that are not yet present in the inspected production database. These migrations remain unapplied until controlled deployment authorization.
- The repository does not yet constitute a complete clean-room reconstruction of the full historical database migration chain; migration baseline/drift reconciliation remains a P0 deployment task.
- Vercel is configured to build `apps/web` with Vite; fresh deployment/runtime verification is still pending.
- Docker Compose includes the known containerizable backend, worker and internal service set including Founder Agent, RECLAIM, VERITAS and NL-to-SQL.

## Gates

| Gate | Status | Evidence / remaining work |
|---|---|---|
| Original P0/P1 audit closure | IN PROGRESS | Foundation and security changes exist, but every original finding still needs explicit regression evidence. |
| Device/event contracts | IN PROGRESS | Versioned contracts, device-gateway tests and Event Bus tests exist. Full cross-service execution remains. |
| Authentication | VERIFIED-PARTIAL | Security API, device ingress, VERITAS and NL-to-SQL have explicit authentication boundaries. Full endpoint/worker negative sweep remains. |
| Authorization | VERIFIED-PARTIAL | Organization membership and resource ownership checks exist in major paths; full resource/action matrix remains. |
| Tenant isolation | VERIFIED-PARTIAL | Human JWT organization claims are checked against membership; device and recovery ownership are bound to tenant records. Full cross-tenant integration suite remains. |
| Privacy / DSAR | IMPLEMENTED, VERIFY | PRV-056 code and migration staged; live schema deployment and end-to-end erasure/legal-hold verification remain. |
| Trust / anti-fraud | IMPLEMENTED, VERIFY | TRU-057 code and migration staged; live schema and production signal/rule validation remain. |
| FinOps | IMPLEMENTED, VERIFY | FIN-058 code and migration staged; billing integration and anomaly validation remain. |
| Runbook | IMPLEMENTED, VERIFY | RNB-059 code and migration staged; OBSERVE-to-Runbook alert integration remains. |
| Frontend source | IMPLEMENTED, VERIFY | `apps/web` contains the React/Vite console; authentication/session lifecycle and production runtime verification remain. |
| Frontend deployment | FIXED-PENDING-VERIFY | Vercel configuration explicitly builds `apps/web`; a fresh deployment must prove the dashboard artifact is served. |
| Accessibility | AUTOMATED | WCAG AA audit is part of Web CI; production browser verification remains. |
| API build/test coverage | VERIFIED | API CI previously passed TypeScript typecheck and security-boundary tests. New cross-service gates require CI execution. |
| Security / OWASP API | NOT CERTIFIED | Full BOLA/IDOR, auth, property authorization, SSRF, resource exhaustion, misconfiguration and inventory tests remain. |
| Internal service authentication | IN PROGRESS | NL-to-SQL and VERITAS use dedicated service secrets; RECLAIM uses a dedicated worker secret and persisted-job validation. Uniform workload identity and KEY caller authorization remain. |
| RECLAIM job boundary | IMPLEMENTED, VERIFY | Worker rejects target/type/device mismatches and completed-job replay; focused security tests exist. Atomic multi-worker claim still requires real DB concurrency evidence. |
| Event Bus delivery boundary | NOT CERTIFIED | Atomic claim/lease, duplicate suppression and concurrent-worker behavior require integration tests against the real persistence layer. |
| Founder gate | NOT CERTIFIED | FND-026 TTL, single-use, signed receipt and complete critical-action routing require executable evidence. |
| Integration / E2E | NOT CERTIFIED | Complete machine registration → credential auth → telemetry → Event Bus → ontology/intelligence → command → acknowledgement → audit/ledger flow remains. |
| Failure / chaos | NOT CERTIFIED | Service, database, Event Bus, network, worker, storage and recovery failure scenarios remain. |
| Load | NOT CERTIFIED | Required targets: 10k msg/s for 5 min, p95 <250 ms, p99 <800 ms; 500 concurrent API reads p95 <300 ms; errors <1%. |
| Backup / restore | NOT CERTIFIED | RTO ≤1h and RPO ≤5m must be demonstrated. |
| Deployment | IN PROGRESS | Compose coverage is reconciled and Vercel build path corrected; clean deployment, canary and rollback evidence remain. |
| Runtime verification | NOT CERTIFIED | Requires deployed smoke tests and production-like runtime evidence. |
| Database performance | IN PROGRESS | RLS/index/performance cleanup must be verified against load targets. |
| Storage | NOT PROVISIONED | Live project currently has 0 storage buckets and 0 storage policies while certificate/Meet workflows expect storage. |
| Migration reproducibility | NOT CERTIFIED | Canonical baseline and schema-drift reconciliation remain mandatory before production migration. |
| Security regression CI | IMPLEMENTED, VERIFY | Cross-service Python security tests, secret-pattern scanning and Compose secret coverage are now defined in `pulse-security-gates.yml`. |

## Recent hardening changes

The Spring Security boundary now permits only the device-gateway path through the human authentication layer; the gateway itself requires a bearer device credential and binds identity/tenant from `key_devices`. A centralized organization membership filter rejects authenticated users whose JWT organization is not an actual membership.

NL-to-SQL `/convert` requires `PULSE_NL_TO_SQL_SERVICE_SECRET`. VERITAS `/analyze` requires `PULSE_VERITAS_SERVICE_SECRET` and verifies evidence/session ownership before writing analysis. RECLAIM validates requests against persisted recovery jobs and has focused tampering/replay tests.

Compose now includes Founder Agent, RECLAIM, VERITAS and NL-to-SQL. The new security workflow adds cross-service compilation/tests, repository secret-pattern checks, dangerous-code-pattern checks and Compose secret consistency checks.

These are implementation corrections, not production-certification claims.

## Security gate matrix

See `docs/security-gate-matrix.md` for the explicit authentication, authorization, tenant, service identity, Event Bus, storage, Founder and migration gates.

## Database advisory

The current Supabase security advisor reports a warning for the `vector` extension being installed in `public`. The current database uses `public.vector` for `find_embeddings.embedding`. Remediation must be performed through a controlled migration after checking vector operators/functions and search paths; it is not being changed blindly in production.

## Certification rule

Do not label PULSE production-ready until all NOT CERTIFIED gates have executable evidence and all deployment/runtime blockers have an approved resolution. Green CI alone is insufficient.
