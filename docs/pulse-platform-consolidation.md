# PULSE Platform Consolidation

## Canonical architecture

`infosalechara-bot/ivonix_dev_proj` is the canonical PULSE monorepo. It contains the public IVONIX/PULSE landing surface and the authenticated PULSE application.

- `index.html` — legacy/static IVONIX/PULSE public landing page.
- `ivonix-site.html` — legacy/static landing artifact.
- `apps/web/` — canonical authenticated PULSE web application.
- `apps/ontology/` — canonical Java API/service layer.
- `apps/*` — canonical supporting services/workers.
- `supabase/migrations/` — canonical migration source, subject to migration reproducibility controls.
- `packages/pulse-contracts/` — canonical cross-service contracts.

## Deployment finding

The Vercel team currently contains two projects linked to the same GitHub repository:

1. `ivonix-dev-proj` — the canonical PULSE deployment. It owns `pulse-ivonix.site` and `www.pulse-ivonix.site` plus Vercel aliases.
2. `ivonix-web-dashboard-preview` — a second Vercel project linked to the same repository, with no custom domain currently listed. It is a duplicate deployment surface and should not become a second production authority.

`ivonix.site` is not listed as a domain on the inspected canonical Vercel project. Its current hosting/DNS target must therefore be explicitly mapped before it can be considered the canonical PULSE entry point.

## Runtime integration finding

The authenticated React application uses `VITE_API_BASE_URL` and currently falls back to `http://localhost:8081/api/v1`. A deployed browser cannot use localhost as the production API. Production must provide the canonical API origin or an explicitly configured same-origin proxy. This is a direct explanation for deployed `Failed to fetch` symptoms when the environment is not configured.

## Target state

```text
IVONIX public entry
        |
        v
canonical PULSE web app (`apps/web`)
        |
        v
canonical Java API (`apps/ontology`)
        |
        +-- Supabase/PostgreSQL
        +-- Event Bus
        +-- Python services/workers
        +-- shared PULSE contracts
        +-- security / tenant authorization
```

No second frontend, backend, database, or service contract should be created for the same PULSE product.

## Consolidation gates

1. Map `ivonix.site` DNS/hosting to the canonical public entry.
2. Keep `ivonix-dev-proj` as the only production Vercel authority.
3. Keep `ivonix-web-dashboard-preview` non-authoritative; it must not receive a production custom domain.
4. Configure the production API origin for `apps/web`.
5. Verify authentication/session bootstrap.
6. Verify every module endpoint from the deployed browser.
7. Verify organization isolation for every module.
8. Verify required database migrations are actually applied.
9. Verify the complete machine flow end-to-end.
10. Retire/quarantine legacy duplicate landing artifacts after the canonical public entry is verified.

## Status semantics

- **Implemented** — source exists.
- **Committed** — source is on the hardening branch.
- **CI verified** — automated checks passed for the exact commit.
- **Deployed** — exact commit is in the target runtime.
- **Runtime verified** — live behavior was exercised successfully.
- **Production certified** — all security, tenant, failure, load, migration, deployment, and machine-flow gates passed.
