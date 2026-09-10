-- Execute against a staging/ephemeral database only.
-- This is a two-session harness specification for the actual transcript-worker RPC.
-- Run Session A and Session B concurrently with the same eligible recording.

select to_regprocedure('public.claim_meet_recording(text,integer)') as claim_function;

-- Session A:
--   select * from public.claim_meet_recording('worker-a', 300);
-- Session B, started while A is still active:
--   select * from public.claim_meet_recording('worker-b', 300);
-- Required: the same recording id must appear in at most one result set.

-- After A's lease expires, B must be able to reclaim the same recording:
--   select * from public.claim_meet_recording('worker-b', 30);

-- Required invariants:
-- 1. FOR UPDATE SKIP LOCKED prevents two workers from claiming the same row concurrently.
-- 2. Each successful claim records worker identity, lease expiry and increments attempts.
-- 3. Completion/failure by a non-owner updates zero rows.
-- 4. An expired lease becomes claimable by another worker.
-- 5. A non-expired lease remains unavailable to another worker.

-- The repository test verifies the SQL contract; actual concurrent execution belongs in
-- staging/ephemeral CI where two independent DB sessions and result capture are available.
