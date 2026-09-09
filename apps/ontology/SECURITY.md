# PULSE Ontology Security Foundation

## Authentication

The service uses Supabase Auth for credential verification, so application passwords remain managed and hashed by the identity provider. PULSE then issues its own short-lived HS256 access token containing the authenticated user and selected organization.

Required environment variables:

- `DATABASE_URL`
- `DATABASE_PASSWORD`
- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`
- `PULSE_JWT_SECRET` — at least 32 bytes; use a high-entropy secret in production

Optional:

- `PULSE_ACCESS_TOKEN_SECONDS` — default 900, constrained to 60–3600
- `PULSE_REFRESH_TOKEN_SECONDS` — default 2592000 (30 days), constrained to 1–90 days
- `PULSE_RATE_LIMIT_PER_MINUTE` — default 100
- `PULSE_RATE_LIMIT_MAX_CLIENTS` — default 10000

## Refresh tokens

Refresh tokens are cryptographically random opaque values. Only SHA-256 hashes are persisted. Each refresh rotates the previous token under a pessimistic database lock, preventing concurrent reuse of the same token.

## Organization isolation

The organization supplied at login is checked against `organization_members`. Subsequent access tokens carry that organization identifier. Ontology APIs derive the organization from the verified token rather than trusting a client-controlled organization header.

## Rate limiting

Bucket4j applies a bounded per-IP token bucket. The bounded cache prevents an attacker from creating an unbounded number of in-memory entries. For horizontally scaled production deployments, replace this local limiter with a shared Redis-backed limiter so all instances enforce one global policy.

## Audit logging

Authentication events and successful authenticated API requests are written to `audit_logs`. Secrets, passwords, JWTs, and refresh token values are never written to the audit table.

## Transport and headers

The security chain is stateless, disables CSRF for bearer-token APIs, enables HSTS, and sends a restrictive Content-Security-Policy. TLS termination must be configured at the ingress/load balancer.
