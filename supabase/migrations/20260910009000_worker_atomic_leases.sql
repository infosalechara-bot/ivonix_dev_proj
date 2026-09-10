-- Worker leases prevent duplicate processing when multiple workers poll the same queue.
-- Intentionally staged: production must receive this only through the controlled migration path.

ALTER TABLE public.meet_recordings
  ADD COLUMN IF NOT EXISTS transcription_worker_id text,
  ADD COLUMN IF NOT EXISTS transcription_lease_until timestamptz,
  ADD COLUMN IF NOT EXISTS transcription_attempts integer NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_meet_recordings_transcription_lease
  ON public.meet_recordings(status, transcription_lease_until, transcription_started_at);

CREATE OR REPLACE FUNCTION public.claim_meet_recording(
  p_worker_id text,
  p_lease_seconds integer DEFAULT 300
)
RETURNS SETOF public.meet_recordings
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_catalog
AS $$
DECLARE
  v_lease integer := LEAST(GREATEST(COALESCE(p_lease_seconds, 300), 30), 3600);
BEGIN
  IF p_worker_id IS NULL OR length(trim(p_worker_id)) < 8 OR length(p_worker_id) > 128 THEN
    RAISE EXCEPTION 'invalid worker id';
  END IF;

  RETURN QUERY
  WITH candidate AS (
    SELECT id
    FROM public.meet_recordings
    WHERE status = 'processing'
      AND (
        transcription_lease_until IS NULL
        OR transcription_lease_until < now()
      )
      AND (transcription_started_at IS NULL OR transcription_lease_until < now())
    ORDER BY COALESCE(transcription_started_at, created_at), id
    FOR UPDATE SKIP LOCKED
    LIMIT 1
  ), claimed AS (
    UPDATE public.meet_recordings r
       SET transcription_started_at = COALESCE(r.transcription_started_at, now()),
           transcription_worker_id = p_worker_id,
           transcription_lease_until = now() + make_interval(secs => v_lease),
           transcription_attempts = r.transcription_attempts + 1
      FROM candidate c
     WHERE r.id = c.id
    RETURNING r.*
  )
  SELECT * FROM claimed;
END;
$$;

REVOKE ALL ON FUNCTION public.claim_meet_recording(text, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.claim_meet_recording(text, integer) TO service_role;
