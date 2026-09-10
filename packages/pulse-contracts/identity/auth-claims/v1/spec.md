# PULSE Auth Claims v1

Additive application JWT contract. Existing `organization_id` remains. Newly issued tokens add `org_id`, `role`, `scopes`, `actor_type`, `session_id`, `iss`, and `aud`; legacy tokens may be accepted only by an explicitly configured transition validator. Server-side tenant/resource authorization remains mandatory.
