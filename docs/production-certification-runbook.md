# PULSE production certification runbook

Production certification is evidence-based. Code presence or green unit tests alone is insufficient.

## Gate order

1. Build and static/security CI
2. Canonical database baseline verification
3. Migration dry-run and schema/RLS verification
4. Authentication and authorization negative tests
5. Cross-tenant BOLA/IDOR tests
6. Device registration → credential → telemetry → Event Bus → intelligence → command → acknowledgement → Ledger E2E
7. Worker restart/lease/retry/dead-letter tests
8. Founder critical-action TTL/single-use/replay tests
9. Storage isolation and signed-object lifecycle tests
10. Failure/chaos tests
11. 10k messages/sec sustained for 5 minutes
12. 500 concurrent API reads
13. Backup/restore proving RTO ≤ 1h and RPO ≤ 5m
14. Canary deployment and rollback
15. Runtime smoke verification
16. Final sign-off

## Required evidence

Every gate must produce a machine-readable or otherwise reviewable artifact under `artifacts/pulse-certification/` or an external evidence reference recorded in the release record.

Required deployment variables for the gate runner:

- `PULSE_DEPLOYMENT_ID`
- `PULSE_EXPECTED_SCHEMA_VERSION`

Load evidence is only considered valid when the test actually executes with `PULSE_RUN_LOAD=1`. A skipped load test is not a pass.

## Production database rule

Do not apply staged migrations directly from a developer branch. First reconcile the canonical baseline, run the migration against a production-like clone/staging database, verify schema/RLS/index/storage state, and obtain the required production migration approval.

## Certification rule

Any unexecuted, skipped, flaky, or manually asserted gate is **NOT CERTIFIED**. The final status must identify the exact evidence for every gate and the deployment version tested.
