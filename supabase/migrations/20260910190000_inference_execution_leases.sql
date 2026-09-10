-- PULSE hardening migration: inference execution state machine.
-- Intentionally independent of the Block 1 migration-baseline reconciliation.
-- Do not apply to production until staging concurrency evidence is green.

ALTER TABLE public.inference_jobs
  ADD COLUMN IF NOT EXISTS lease_owner text,
  ADD COLUMN IF NOT EXISTS lease_expires_at timestamptz,
  ADD COLUMN IF NOT EXISTS attempt integer NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS max_attempts integer NOT NULL DEFAULT 3,
  ADD COLUMN IF NOT EXISTS idempotency_key text,
  ADD COLUMN IF NOT EXISTS failure_reason text;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'inference_jobs_status_check'
  ) THEN
    ALTER TABLE public.inference_jobs
      ADD CONSTRAINT inference_jobs_status_check
      CHECK (status IN ('queued','running','completed','failed','recoverable'));
  END IF;
END $$;

ALTER TABLE public.inference_jobs
  DROP CONSTRAINT IF EXISTS inference_jobs_attempt_check,
  DROP CONSTRAINT IF EXISTS inference_jobs_max_attempts_check;

ALTER TABLE public.inference_jobs
  ADD CONSTRAINT inference_jobs_attempt_check CHECK (attempt >= 0),
  ADD CONSTRAINT inference_jobs_max_attempts_check CHECK (max_attempts > 0 AND max_attempts <= 100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_inference_jobs_org_idempotency
  ON public.inference_jobs (organization_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_inference_jobs_claimable
  ON public.inference_jobs (status, lease_expires_at)
  WHERE status IN ('queued','recoverable');

CREATE OR REPLACE FUNCTION public.claim_inference_job(
  p_job_id uuid,
  p_org_id uuid,
  p_lease_owner text,
  p_lease_seconds integer DEFAULT 60
)
RETURNS TABLE (
  job_id uuid,
  model_id uuid,
  payload jsonb,
  attempt integer
)
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public
AS $$
BEGIN
  IF p_lease_owner IS NULL OR length(trim(p_lease_owner)) < 8 THEN
    RAISE EXCEPTION 'invalid lease owner';
  END IF;
  IF p_lease_seconds < 5 OR p_lease_seconds > 3600 THEN
    RAISE EXCEPTION 'invalid lease duration';
  END IF;

  UPDATE public.inference_jobs AS j
     SET status = 'running',
         lease_owner = p_lease_owner,
         lease_expires_at = now() + make_interval(secs => p_lease_seconds),
         attempt = j.attempt + 1,
         started_at = COALESCE(j.started_at, now()),
         failure_reason = NULL
   WHERE j.id = p_job_id
     AND j.organization_id = p_org_id
     AND j.status IN ('queued','recoverable')
     AND (j.status = 'queued' OR j.lease_expires_at IS NULL OR j.lease_expires_at < now())
  RETURNING j.id, j.model_id, j.input_data, j.attempt
  INTO job_id, model_id, payload, attempt;

  IF job_id IS NULL THEN
    RETURN;
  END IF;

  RETURN NEXT;
END;
$$;

CREATE OR REPLACE FUNCTION public.renew_inference_lease(
  p_job_id uuid,
  p_org_id uuid,
  p_lease_owner text,
  p_lease_seconds integer DEFAULT 60
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_rows integer;
BEGIN
  IF p_lease_seconds < 5 OR p_lease_seconds > 3600 THEN
    RAISE EXCEPTION 'invalid lease duration';
  END IF;

  UPDATE public.inference_jobs
     SET lease_expires_at = now() + make_interval(secs => p_lease_seconds)
   WHERE id = p_job_id
     AND organization_id = p_org_id
     AND lease_owner = p_lease_owner
     AND status = 'running'
     AND lease_expires_at >= now();
  GET DIAGNOSTICS v_rows = ROW_COUNT;
  RETURN v_rows = 1;
END;
$$;

CREATE OR REPLACE FUNCTION public.complete_inference_job(
  p_job_id uuid,
  p_org_id uuid,
  p_lease_owner text,
  p_result jsonb,
  p_latency_ms numeric DEFAULT NULL
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_rows integer;
BEGIN
  UPDATE public.inference_jobs
     SET status = 'completed',
         output_data = p_result,
         latency_ms = COALESCE(p_latency_ms, latency_ms),
         completed_at = now(),
         lease_owner = NULL,
         lease_expires_at = NULL
   WHERE id = p_job_id
     AND organization_id = p_org_id
     AND lease_owner = p_lease_owner
     AND status = 'running'
     AND lease_expires_at >= now();
  GET DIAGNOSTICS v_rows = ROW_COUNT;
  RETURN v_rows = 1;
END;
$$;

CREATE OR REPLACE FUNCTION public.fail_inference_job(
  p_job_id uuid,
  p_org_id uuid,
  p_lease_owner text,
  p_reason text,
  p_latency_ms numeric DEFAULT NULL
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_attempt integer;
  v_max_attempts integer;
  v_rows integer;
BEGIN
  SELECT attempt, max_attempts
    INTO v_attempt, v_max_attempts
    FROM public.inference_jobs
   WHERE id = p_job_id
     AND organization_id = p_org_id
     AND lease_owner = p_lease_owner
     AND status = 'running'
     AND lease_expires_at >= now()
   FOR UPDATE;

  IF NOT FOUND THEN
    RETURN false;
  END IF;

  UPDATE public.inference_jobs
     SET status = CASE WHEN v_attempt < v_max_attempts THEN 'recoverable' ELSE 'failed' END,
         failure_reason = left(COALESCE(p_reason, 'inference_failed'), 500),
         latency_ms = COALESCE(p_latency_ms, latency_ms),
         completed_at = CASE WHEN v_attempt >= v_max_attempts THEN now() ELSE NULL END,
         lease_owner = NULL,
         lease_expires_at = NULL
   WHERE id = p_job_id
     AND organization_id = p_org_id
     AND lease_owner = p_lease_owner
     AND status = 'running';
  GET DIAGNOSTICS v_rows = ROW_COUNT;
  RETURN v_rows = 1;
END;
$$;

CREATE OR REPLACE FUNCTION public.sweep_expired_inference_leases()
RETURNS integer
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_count integer;
BEGIN
  WITH expired AS (
    UPDATE public.inference_jobs
       SET status = CASE WHEN attempt < max_attempts THEN 'recoverable' ELSE 'failed' END,
           failure_reason = 'lease_expired',
           lease_owner = NULL,
           lease_expires_at = NULL,
           completed_at = CASE WHEN attempt >= max_attempts THEN now() ELSE NULL END
     WHERE status = 'running'
       AND lease_expires_at IS NOT NULL
       AND lease_expires_at < now()
    RETURNING id
  )
  SELECT count(*) INTO v_count FROM expired;
  RETURN v_count;
END;
$$;

COMMENT ON FUNCTION public.claim_inference_job(uuid,uuid,text,integer) IS
  'Atomic compare-and-set claim for inference execution; one concurrent caller can win a queued/recoverable job.';
COMMENT ON FUNCTION public.renew_inference_lease(uuid,uuid,text,integer) IS
  'Renews an active inference lease only for the owning tenant and lease owner.';
COMMENT ON FUNCTION public.complete_inference_job(uuid,uuid,text,jsonb,numeric) IS
  'Completes an inference job only while its unexpired lease is held by the caller.';
COMMENT ON FUNCTION public.fail_inference_job(uuid,uuid,text,text,numeric) IS
  'Fails or returns an inference job to recoverable state only while its unexpired lease is held.';
COMMENT ON FUNCTION public.sweep_expired_inference_leases() IS
  'Recovers expired inference leases; schedule from pg_cron or an external worker.';
