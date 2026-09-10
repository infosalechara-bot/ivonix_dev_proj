# PULSE Platform Consolidation

## Canonical architecture

`infosalechara-bot/ivonix_dev_proj` is the canonical PULSE monorepo. It contains both the public IVONIX/PULSE landing surface and the authenticated PULSE application.

### Surfaces

- `index.html` — legacy/static IVONIX/PULSE public landing page.
- `ivonix-site.html` — large legacy/static landing artifact; not the authenticated PULSE application.
- `apps/web/` — canonical authenticated PULSE web application.
- `apps/ontology/` — canonical Java API/service layer.
- `apps/*` — canonical supporting Python services/workers.
- `supabase/migrations/` — canonical database migration source, subject to migration reproducibility controls.
- `packages/pulse-contracts/` — canonical cross-service contract definitions.

## Current production topology finding

The Vercel project `ivonix-dev-proj` is connected to this repository and currently has the domains `pulse-ivonix.site` and `www.pulse-ivonix.site` (plus Vercel deployment aliases). The repository root contains a separate static landing page, while the Vercel configuration builds `apps/web`.

Therefore the two experiences can currently appear to be different websites even though they live in the same repository: one is a static/root surface and the other is the canonical React application.

`ivonix.site` is not listed as a domain on the inspected Vercel project. It must not be treated as the canonical PULSE application until its hosting/DNS target is explicitly mapped to the canonical deployment.

## Required target state

```text
IVONIX
  |
  +-- public landing
  |      |
  |      +-- canonical PULSE application entry
  |
  +-- authenticated PULSE application (`apps/web`)
         |
         +-- canonical API (`apps/ontology`)
         +-- supporting services (`apps/*`)
         +-- Supabase/PostgreSQL
         +-- Event Bus
         +-- shared contracts
         +-- security/tenant authorization
```

There must not be a second independent PULSE backend, frontend, database, or service contract created to support the public site.

## Immediate integration blocker

The authenticated web application currently derives its API base URL from `VITE_API_BASE_URL`, but falls back to `http://localhost:8081/api/v1`. That fallback is valid only for local development. A deployed browser cannot use localhost to reach the production API.

Production must provide `VITE_API_BASE_URL` or an explicitly configured same-origin `/api/v1` proxy to the canonical Java API. The value must never silently default to localhost in a production build.

## Consolidation gates

1. Identify the DNS/hosting owner for `ivonix.site`.
2. Point the public domain to the canonical PULSE deployment or explicitly route it to the canonical application entry.
3. Configure the production API origin for `apps/web`.
4. Verify authentication/session bootstrap against the canonical API.
5. Verify every PULSE module endpoint from the deployed browser.
6. Verify organization isolation for every module.
7. Verify database schema/migrations used by each module.
8. Remove or quarantine legacy duplicate landing artifacts after the canonical surface is verified.
9. Perform machine-flow E2E certification.

## Status semantics

- **Implemented**: source code exists in the canonical repository.
- **Committed**: source is committed to the hardening branch.
- **CI verified**: automated checks passed for the exact commit.
- **Deployed**: the exact commit is present in the target runtime.
- **Runtime verified**: live requests and state transitions were exercised successfully.
- **Production certified**: all security, tenant, failure, load, migration, deployment, and machine-flow gates passed.

The presence of a UI module or deployment does not by itself satisfy the last three states.
