# AppForge tenant admission and DDL safety contract

## Invariants
- App creation is serialized per organization for admission decisions.
- An organization cannot exceed its configured app, generated-table, or concurrent-provisioning quota.
- A single app cannot declare more than 30 models or 512 total fields; each generated model is capped at 64 fields.
- Generated SQL identifiers and types remain allowlisted; arbitrary SQL is never accepted from the request.
- Provisioning and generated DDL execute under bounded PostgreSQL statement/lock timeouts.
- Quota rejection occurs before app metadata is published.

## Required negative cases
- concurrent creates racing at the same tenant quota boundary
- app quota exceeded
- generated-table quota exceeded
- concurrent-provisioning capacity exhausted
- 31 models
- more than 512 total fields
- more than 64 fields in one model
- invalid identifier
- unsupported field type
- DDL lock/statement timeout
- cross-tenant generated-table access

## Release evidence
- admission-limit migration applied to a disposable PostgreSQL instance
- concurrent quota test proves no quota oversubscription
- provisioning rollback test proves failed DDL leaves no READY app or partial registry
- security test proves another tenant cannot consume generated tables

Code existence is not verification. Production certification requires the concurrency, rollback, and tenant-isolation tests to execute successfully in CI and against disposable PostgreSQL.
