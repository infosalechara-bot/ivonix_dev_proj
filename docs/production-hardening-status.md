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
- The live migration history now includes database-side hardening versions through `20260910105537`, while the repository migration directory does not contain a matching complete historical chain. This confirms migration-history/schema reproducibility remains a P0 deployment task.
- The repository contains staged migrations for additional Academy/Global/Meet and Privacy/Trust/FinOps/Runbook schemas that are not yet present in the inspected production database. These migrations remain unapplied until controlled deployment authorization.
- Vercel is configured to build `apps/web` with Vite; fresh deployment/runtime verification is still pending.
- Docker Compose includes the known containerizable backend, worker and internal service set including Founder Agent, RECLAIM, VERITAS and NL-to-SQL.

## Gates

| Gate | Status | Evidence / remaining work |
|---|---|---|
| Original P0/P1 audit closure | IN PROGRESS | Foundation and security changes exist, but every original finding still needs explicit regression evidence. |
| Device/event contracts | IN PROGRESS | Versioned contracts, device-gateway tests and Event Bus tests exist. Full cross-service execution remains. |
| Authentication | VERIFIED-PARTIAL | Security API, device ingress, VERITAS and NL-to-SQL have explicit authentication boundaries. Full endpoint/worker negative sweep remains. |
| Authorization | VERIFIED-PARTIAL | Organization membership and resource ownership checks exist in major paths; full resource/action matrix remains. |
| Tenant isolation | VERIFIED-PARTIAL | Human JWT organization claims are checked against membership; device, recovery and KEY resource boundaries have explicit tenant binding. Full cross-tenant integration suite remains. |
| Privacy / DSAR | IMPLEMENTED, VERIFY | PRV-056 code and migration staged; live schema deployment and end-to-end erasure/legal-hold verification remain. |
| Trust / anti-fraud | IMPLEMENTED, VERIFY | TRU-057 code and migration staged; live schema and production signal/rule validation remain. |
| FinOps | IMPLEMENTED, VERIFY | FIN-058 code and migration staged; billing integration and anomaly validation remain. |
| Runbook | IMPLEMENTED, VERIFY | RNB-059 code and migration staged; OBSERVE-to-Runbook alert integration remains. |
| Frontend source | IMPLEMENTED, VERIFY | `apps/web` contains the React/Vite console; authentication/session lifecycle and production runtime verification remain. |
| Frontend deployment | FIXED-PENDING-VERIFY | Vercel configuration explicitly builds `apps/web`; a fresh deployment must prove the dashboard artifact is served. |
| Accessibility | AUTOMATED | WCAG AA audit is part of Web CI; production browser verification remains. |
| API build/test coverage | VERIFIED-PARTIAL | Existing API security tests cover authentication boundaries; the authenticated read route is now covered by regression tests. New cross-service gates require CI execution. |
| Security / OWASP API | NOT CERTIFIED | Full BOLA/IDOR, auth, property authorization, SSRF, resource exhaustion, misconfiguration and inventory tests remain. |
| Internal service authentication | IN PROGRESS | NL-to-SQL and VERITAS use dedicated service secrets; RECLAIM uses a dedicated worker secret; KEY binds caller and tenant through its current service boundary. Uniform cryptographic workload identity remains. |
| RECLAIM job boundary | IMPLEMENTED, VERIFY | Worker rejects target/type/device mismatches and completed-job replay; focused security tests exist. Atomic multi-worker claim still requires real DB concurrency evidence. |
| Event Bus delivery boundary | IMPLEMENTED, VERIFY | Added atomic DB claim/lease with `FOR UPDATE SKIP LOCKED`, worker-bound terminal updates, bounded event/webhook payloads and redirect-disabled SSRF defenses. Real migration execution, two-worker race, lease-expiry recovery and duplicate-side-effect evidence remain mandatory. |
| Founder gate | NOT CERTIFIED | FND-026 TTL, action/resource binding, single-use, signed receipt and complete critical-action routing require executable evidence. |
| Integration / E2E | NOT CERTIFIED | Complete machine registration → credential auth → telemetry → Event Bus → ontology/intelligence → command → acknowledgement → audit/ledger flow remains. |
| Failure / chaos | NOT CERTIFIED | Service, database, Event Bus, network, worker, storage and recovery failure scenarios remain. |
| Load | IMPLEMENTED, VERIFY | Executable k6 profiles exist for the 10k msg/s sustained machine workload and 500-VU authenticated API read workload. Actual target execution/evidence remains mandatory. |
| Backup / restore | NOT CERTIFIED | RTO ≤1h and RPO ≤5m must be demonstrated. |
| Deployment | IN PROGRESS | Compose coverage is reconciled and Vercel build path corrected; clean deployment, canary and rollback evidence remain. |
| Runtime verification | NOT CERTIFIED | Requires deployed smoke tests and production-like runtime evidence. |
| Database performance | IN PROGRESS | RLS/index/performance cleanup must be verified against load targets. |
| Storage | STAGED, NOT PROVISIONED | Private tenant-isolated `pulse-assets` bucket/policies remain staged; transcript worker is aligned to that bucket and tenant-prefix contract, but live storage remains unchanged at 0 buckets/0 policies. |
| Migration reproducibility | NOT CERTIFIED | Live migration history and repository chain remain divergent; canonical baseline and schema-drift reconciliation are mandatory before production migration. Read-only schema/RLS verification is present at `scripts/verify-schema.sql`. |
| Security regression CI | IMPLEMENTED, VERIFY | Cross-service Python security tests, secret-pattern scanning and Compose secret coverage are defined in `pulse-security-gates.yml`. |
| Machine-flow certification harness | IMPLEMENTED, VERIFY | `tests/integration/machine-flow-gates.md` defines the complete registration → telemetry → Event Bus → intelligence → command → audit → recovery → Founder → runtime chain and certification thresholds. |

## Recent hardening changes

The Spring Security boundary now permits only the device-gateway path through the human authentication layer; the gateway itself requires a bearer device credential and binds identity/tenant from `key_devices`. A centralized organization membership filter rejects authenticated users whose JWT organization is not an actual membership.

NL-to-SQL `/convert` requires `PULSE_NL_TO_SQL_SERVICE_SECRET`. VERITAS `/analyze` requires `PULSE_VERITAS_SERVICE_SECRET` and verifies evidence/session ownership before writing analysis. RECLAIM validates requests against persisted recovery jobs and has focused tampering/replay tests.

KEY crypto operations now require an allowlisted caller identity and an explicit organization ID, and active-key lookup is constrained by both key ID and organization. Operation audit records carry tenant/caller context. HSM/KMS remains a production requirement.

The authenticated API read workload now targets a real `GET /api/v1/security/health` route instead of a previously nonexistent endpoint. The endpoint is explicitly bearer-authenticated and covered by controller regression tests.

Transcript processing now uses the canonical private `pulse-assets` bucket by default, rejects traversal and tenant-prefix mismatches, and enforces the staged 50 MB storage limit. Atomic worker claiming remains lease-based and worker-bound.

Event Bus delivery now has a staged atomic ownership lease: `claim_event_delivery` uses PostgreSQL row locking with `SKIP LOCKED`, leases are bounded, terminal updates require the current worker and an unexpired lease, and event/webhook payloads are bounded before persistence/delivery. Webhook redirects remain disabled and resolved destinations are checked against non-public address classes. This is an implementation correction; it is not yet an integration certification.

Compose includes Founder Agent, RECLAIM, VERITAS and NL-to-SQL. The security workflow adds cross-service compilation/tests, repository secret-pattern checks, dangerous-code-pattern checks and Compose secret consistency checks.

A private tenant-isolated storage foundation is staged but deliberately not applied to production. Read-only schema/RLS verification and executable k6 load profiles are part of the hardening artifacts. The complete machine-flow certification sequence is documented for integration execution.

These are implementation corrections, not production-certification claims.

## Security gate matrix

See `docs/security-gate-matrix.md` for the explicit authentication, authorization, tenant, service identity, Event Bus, storage, Founder and migration gates.

## Database advisory

The current Supabase security advisor reports a warning for the `vector` extension being installed in `public`. The current database uses `public.vector` for `find_embeddings.embedding`. Remediation must be performed through a controlled migration after checking vector operators/functions and search paths; it is not being changed blindly in production.

## Certification rule

Do not label PULSE production-ready until all NOT CERTIFIED gates have executable evidence and all deployment/runtime blockers have an approved resolution. Green CI alone is insufficient.
