-- Execute against a staging/ephemeral PostgreSQL database only.
-- Two independent sessions must run the claim statements concurrently.

select to_regprocedure('public.claim_event_delivery(text,integer)') as claim_function;

-- Session A:
--   begin;
--   select * from public.claim_event_delivery('event-worker-a', 30);
-- Keep the transaction open while Session B executes its claim.

-- Session B:
--   begin;
--   select * from public.claim_event_delivery('event-worker-b', 30);

-- Required invariant: for the same eligible delivery row, at most one session
-- receives that delivery id. FOR UPDATE SKIP LOCKED provides the serialization.
-- After the owning lease expires, a later claim by B may reclaim the same row.
-- The stable operation_id must be identical before and after reclaim.

-- Terminal ownership check (run with the captured delivery id/operation_id):
--   update public.event_deliveries
--      set status='delivered', completed_at=now()
--    where id=<delivery_id>
--      and operation_id=<operation_id>
--      and worker_id='event-worker-a'
--      and lease_until > now();
-- Required: a non-owner update affects zero rows.
