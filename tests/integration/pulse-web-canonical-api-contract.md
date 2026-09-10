# PULSE web canonical API contract

## Purpose

The PULSE web application must have exactly one browser API origin. It must never silently fall back to localhost or another backend in a production deployment.

## Required invariants

1. `VITE_API_BASE_URL` is explicitly configured for every production web deployment.
2. The value is an HTTPS origin/path for the canonical PULSE API.
3. No production bundle contains `http://localhost:8081/api/v1` as an API fallback.
4. Browser API calls use the shared `apps/web/src/lib/pulseApi.js` client.
5. Authentication failures clear the access token and organization context.
6. Missing API configuration fails closed with a deployment configuration error.
7. Module calls do not invent independent API origins.
8. Device/resource identifiers are URL-encoded before insertion into API paths.

## Current integration scope

The shared client is wired into AI Studio, App Forge, CORE, Insight, and Digital Twin. The remaining module components must be migrated before this contract is considered fully satisfied.

## Release evidence

A release is only verified when:

- web build succeeds;
- production environment contains the canonical API origin;
- deployed browser can authenticate;
- every module can reach the canonical API;
- no browser request targets localhost;
- 401 clears session state;
- representative cross-tenant negative requests are rejected by the backend;
- production smoke test records the deployed commit SHA and API origin.

A successful Vercel build alone does not constitute API integration verification.
