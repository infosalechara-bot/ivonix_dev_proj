# PULSE database baseline reconciliation

## Current state

The production Supabase project contains a historical schema that is not fully reconstructible from the current branch's migration directory. The branch also contains forward migrations for capabilities that are intentionally not yet applied to production.

## Required procedure

1. Export the production schema metadata and migration history.
2. Reconstruct the historical canonical baseline in a disposable PostgreSQL environment.
3. Apply every repository migration in lexical order from that baseline.
4. Compare tables, columns, constraints, indexes, functions, triggers, RLS, policies and extensions against the intended production schema.
5. Resolve drift explicitly; do not overwrite production to make the repository appear correct.
6. Validate all new migrations against a production-like clone.
7. Run the schema/RLS verification script.
8. Record the resulting schema version as the deployment's `PULSE_EXPECTED_SCHEMA_VERSION`.
9. Obtain the required production migration approval before applying forward migrations.

## Safety requirements

- Never use `DROP ... CASCADE` as a drift shortcut.
- Never disable RLS to make tests pass.
- Never move or replace the `vector` extension without checking dependent operators/functions and search paths.
- Storage buckets and policies must be created through reviewed migrations.
- A migration is not considered deployed because its SQL exists in Git.

## Exit criteria

The database gate is green only when a clean-room reconstruction succeeds, the production-like clone matches expected schema/RLS/policy state, and the deployment records the exact migration/schema version tested.
