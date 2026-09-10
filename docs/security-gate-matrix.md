# PULSE security gate matrix

This matrix is an execution gate, not a certification statement. Every row requires executable evidence before production approval.

| Boundary | Required property | Regression evidence |
|---|---|---|
| Human API | valid JWT required | missing/invalid bearer tests |
| Human API | JWT org is backed by membership | cross-org 403 tests |
| Resource API | requested resource belongs to authorized org | BOLA/IDOR negative tests |
| Device ingress | device credential required | invalid credential tests |
| Device ingress | device determines tenant | forged organization claim tests |
| Service-to-service | dedicated service identity | missing/wrong secret tests |
| RECLAIM | job payload matches persisted job | tamper tests |
| RECLAIM | only one worker claims queued job | concurrent claim test against DB |
| VERITAS | evidence belongs to session | mismatch tests |
| NL-to-SQL | only allowlisted query templates execute | injection/property tests |
| Event Bus | delivery is atomically claimed | duplicate worker race test |
| Event Bus | retry bounded and dead-letters | retry exhaustion test |
| Webhooks | endpoint is safe after DNS resolution | SSRF/private-IP tests |
| KEY | caller is authorized for key | cross-tenant key-use tests |
| Founder gate | critical action requires valid approval | TTL/single-use/replay tests |
| Storage | tenant path and signed access enforced | cross-tenant object tests |
| Database | every tenant table has RLS | migration/schema gate |
| Migrations | clean-room reconstruction works | ephemeral database replay |

## Blocking rule

A passing unit test does not certify a distributed boundary. Resource authorization, tenant isolation, worker claims, storage access, and Event Bus semantics must be tested against a real database or integration environment before certification.
