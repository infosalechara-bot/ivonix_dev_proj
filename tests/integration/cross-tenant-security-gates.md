# Cross-tenant security certification matrix

This suite is a release gate. Every resource must be tested with two organizations and two identities. A request must never gain access merely by replacing an object UUID, organization UUID, device UUID, job UUID, key UUID, or path prefix.

## Required negative cases

| Surface | Attempt | Required result |
|---|---|---|
| Device | Org B user reads Org A device | 403/404, no data |
| Device ingress | Credential from Org A targets Org B device | 401/403, no mutation |
| Event Bus | Org B reads/acknowledges Org A delivery | 403/404, no side effect |
| Digital Twin | Org B patches Org A run/twin/model | 403/404, no mutation |
| AI Training | Org B reads/starts Org A job | 403/404, no artifact access |
| AppForge | Org B reads/changes Org A app/table | 403/404, no DDL |
| KEY | Org A caller supplies Org B key UUID | 404/403, no plaintext/ciphertext |
| Recovery | Org B supplies Org A recovery job ID | 403/404, no state change |
| Reports | Org B requests Org A report | 403/404, no document |
| Privacy | Org B references Org A DSAR | 403/404, no export/erase |
| Trust | Org B references Org A risk/block/report | 403/404, no mutation |
| FinOps | Org B reads Org A profitability/cost data | 403/404, no data |
| Runbook | Org B changes Org A incident | 403/404, no mutation |
| Storage | Org B reads Org A object path | 403/404 |
| Founder | Replays a consumed approval token | 401/403, no action |

## Required positive cases

For each row, repeat the same operation using the owning identity and owning organization. The operation must succeed only when the identity/resource relationship is valid.

## Evidence

Record request, principal, resource organization, response status, mutation count, and audit event ID. A passing status code alone is insufficient: verify that the target row/object did not change and that no sensitive response body was returned.

## Release rule

Any cross-tenant data disclosure, mutation, or side effect is a P0 failure. The deployment is not certifiable until the failing path is fixed and the complete matrix is rerun.
