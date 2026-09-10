-- PULSE Event Bus: expose durable operation identity from the atomic claim path.
-- Forward migration: retries of one delivery must reuse the same operation_id.

CREATE OR REPLACE FUNCTION public.claim_event_delivery(
  p_worker_id text,
  p_lease_seconds integer DEFAULT 60
)
RETURNS TABLE (
  id bigint,
  event_id uuid,
  subscription_id uuid,
  operation_id uuid,
  attempts integer,
  worker_id text,
  lease_until timestamptz,
  endpoint_url text,
  payload jsonb,
  source_service text,
  event_time timestamptz,
  correlation_id text
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
  v_id bigint;
BEGIN
  IF p_worker_id IS NULL OR char_length(p_worker_id) NOT BETWEEN 8 AND 128 THEN
    RAISE EXCEPTION 'invalid worker id';
  END IF;
  IF p_lease_seconds IS NULL OR p_lease_seconds < 15 OR p_lease_seconds > 900 THEN
    RAISE EXCEPTION 'invalid lease seconds';
  END IF;

  SELECT d.id INTO v_id
  FROM public.event_deliveries d
  WHERE d.status IN ('pending','failed')
    AND (d.lease_until IS NULL OR d.lease_until <= now())
    AND d.attempts < 10
    AND (
      d.status = 'pending'
      OR d.last_attempt_at IS NULL
      OR d.last_attempt_at < now() - make_interval(secs => least(300, greatest(5, power(2, least(d.attempts, 8))::integer)))
    )
  ORDER BY coalesce(d.last_attempt_at, to_timestamp(0)), d.id
  FOR UPDATE SKIP LOCKED
  LIMIT 1;

  IF v_id IS NULL THEN
    RETURN;
  END IF;

  UPDATE public.event_deliveries d
  SET worker_id = p_worker_id,
      lease_until = now() + make_interval(secs => p_lease_seconds)
  WHERE d.id = v_id
    AND d.status IN ('pending','failed')
    AND (d.lease_until IS NULL OR d.lease_until <= now())
  RETURNING d.id, d.event_id, d.subscription_id, d.operation_id,
            d.attempts, d.worker_id, d.lease_until
  INTO id, event_id, subscription_id, operation_id,
       attempts, worker_id, lease_until;

  SELECT s.endpoint_url, e.payload, e.source_service, e.event_time, e.correlation_id
  INTO endpoint_url, payload, source_service, event_time, correlation_id
  FROM public.event_subscriptions s
  JOIN public.events e ON e.id = event_id
  WHERE s.id = subscription_id;

  RETURN NEXT;
END;
$$;

REVOKE ALL ON FUNCTION public.claim_event_delivery(text, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.claim_event_delivery(text, integer) TO service_role;

COMMENT ON FUNCTION public.claim_event_delivery(text, integer) IS
  'Atomically claims one delivery and returns its stable operation_id for downstream idempotency.';
