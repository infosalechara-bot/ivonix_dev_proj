-- Execute against a staging/ephemeral database only.
-- The harness must invoke claim_worker_job concurrently from two independent sessions.

begin;

select to_regprocedure('public.claim_worker_job(text,integer,integer)') as claim_function;

-- Required invariants for the atomic lease implementation:
-- 1. Two simultaneous claims for the same eligible row return at most one row.
-- 2. A claim writes a unique lease token and worker identity.
-- 3. A non-owner cannot complete or fail the claimed job.
-- 4. An expired lease becomes claimable by another worker.
-- 5. A non-expired lease remains unavailable to other workers.

-- The actual two-session execution is intentionally outside a single SQL transaction;
-- use psql/pgbench/CI to run these statements concurrently and save both result sets.

rollback;
