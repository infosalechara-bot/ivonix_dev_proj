# AppForge atomic provisioning contract

## Invariant
An AppForge application is externally usable only when its metadata, every generated table, tenant RLS policy, required index, and generated-table registry row have been created successfully.

## Required state transition
`PROVISIONING -> READY` is committed atomically. Any provisioning exception must roll back the transaction so no READY app or partial generated-table registry remains.

## Required negative cases
- invalid model/field identifier
- unsupported field type
- duplicate field
- generated DDL failure
- generated-table registry failure
- cross-tenant table access
- access to a non-READY app

## Release evidence
- migration adding `provisioning_status`
- service method annotated transactional
- status written as `PROVISIONING` before generated DDL
- status changed to `READY` only after all generated tables are registered
- generated-table authorization requires `READY` and matching organization
- integration test demonstrates rollback on injected provisioning failure

Code existence is not verification. Production certification requires the integration failure/rollback test to execute successfully in CI and against a disposable PostgreSQL instance.
