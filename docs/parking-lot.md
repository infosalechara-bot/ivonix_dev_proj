# PULSE Parking Lot

Findings recorded during Block 1 execution are not part of the Golden Path unless they directly block one of the 13 requirements.

## P0 — Migration baseline divergence
- **What:** Production has 41 migrations while the branch contains a materially different 21-file migration set, with bidirectional divergence and timestamp/name differences.
- **Why it matters:** A fresh staging build may not reproduce production schema history and post-state.
- **Block:** Pre-Block-2 gate.
- **Evidence required to close:** Fresh staging build matches production `schema_migrations` and required `event_types` post-state, with authoritative reconciliation evidence.

## P0 — Inference execution race
- **What:** The inference execution path historically allowed concurrent requests to execute the same queued job without an atomic database claim. A lease implementation exists on the branch but is not part of the Block 1 Golden Path.
- **Why it matters:** Concurrent execution can produce duplicate inference work and conflicting job transitions.
- **Block:** Post-Block-1 hardening.
- **Evidence required to close:** Staging concurrency test with 10 parallel claimers produces exactly one winner, plus recovery/fencing evidence.

## P0 — Service-role blast radius
- **What:** Multiple internal services currently use `SUPABASE_SERVICE_ROLE_KEY`, allowing a compromised service to bypass database RLS.
- **Why it matters:** One service compromise can become database-wide authority.
- **Block:** Post-Block-1 hardening, service-by-service.
- **Evidence required to close:** One service migrated to a correctly scoped database authorization boundary; cross-tenant negative tests pass; RLS remains enforced; 7-day observation is clean.

## P1 — Internal service authentication too coarse
- **What:** Internal service authentication includes shared bearer secrets without the full issuer/audience/tenant/replay protections required for the target service identity model.
- **Why it matters:** Shared credentials enlarge trust boundaries and do not provide strong caller identity, audience binding, or replay resistance.
- **Block:** Post-Block-1 hardening.
- **Evidence required to close:** Signed service tokens deployed to a caller/callee pair with issuer, audience, tenant and expiry validation; replay rejected; bearer traffic reaches zero before legacy bearer removal.

## P1 — Model admission / execution authorization
- **What:** Model path traversal is protected, but model admission/version authorization remains separate from the Golden Path.
- **Why it matters:** Ownership alone is insufficient to establish that a model version is approved for execution.
- **Block:** Post-Block-1 hardening.
- **Evidence required to close:** Admission policy enforced at execution boundary with positive and negative authorization tests.

## P1 — Vector extension placement
- **What:** The production security advisor reports the `vector` extension in `public`, with `find_embeddings.embedding` using `public.vector`.
- **Why it matters:** Extension placement can affect schema hygiene and security posture.
- **Block:** Post-Block-1 database hardening.
- **Evidence required to close:** Function/operator/search-path impact assessed and a tested migration or explicit accepted-risk decision recorded.

## P1 — Event schema ID derivation collision risk
- **What:** Event schema IDs are derived from event names using dot-to-underscore replacement, which can collide if dotted event names are introduced later.
- **Why it matters:** Registry identity must remain injective and deterministic.
- **Block:** Post-Block-1 contract hardening.
- **Evidence required to close:** Registry naming constraint or collision-proof schema-ID derivation, with migration and regression tests.

## Block 1 promotion rule
A parked finding may be promoted into Block 1 only when it directly prevents one of the 13 Golden Path requirements from executing or being verified. The promoted fix must be minimal and must not broaden scope beyond that blocker.
