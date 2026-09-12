# BLOCK 1 Certification

## Status

**NOT CERTIFIED** until live runtime execution and the required canary observation are complete.

Repository-side work for the Golden Path harness is complete on this branch, including the command-delivery race fix. Live proof is still required.

## Evidence required for `block-1-verified`

| # | Evidence | Status |
|---|----------|--------|
| 1 | Contract verifier (`scripts/verify_block1.sh`) PASS on the certified commit | Pending execution |
| 2 | Security gates PASS on the certified commit | Pending execution |
| 3 | Runtime / Compose build PASS on the certified commit | Pending execution |
| 4 | Live Golden Path harness PASS (13/13 requirements) | Pending live run |
| 5 | Canary execution PASS | Pending |
| 6 | Seven consecutive days of five-minute canary observations PASS | Pending |

Only after all six rows are green may the tag `block-1-verified` be created.

## Non-equivalence rule

CI compilation, static security scans, contract verification, and deployment readiness do **not** constitute proof of the live machine loop. No release or tag may claim `block-1-verified` without the live evidence above.

## Harness reliability (closed)

The previously identified race (MQTT command listener registered after the concurrent command POSTs) has been fixed. The listener is now attached before command issuance so a fast broker delivery cannot produce a false timeout.

## Cost boundary

No Supabase Preview/staging branch is required for this repository-side certification work. Creating paid infrastructure remains an explicit external operation and is not performed by this certification change.
