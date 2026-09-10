ALTER TABLE public.privacy_dsar_requests ADD COLUMN IF NOT EXISTS result_payload jsonb;

-- Seed the control-plane event vocabulary when the shared event table is present.
DO $$ BEGIN
  IF to_regclass('public.event_types') IS NOT NULL THEN
    INSERT INTO public.event_types(name) VALUES
      ('privacy.consent_recorded'),('privacy.dsar_submitted'),('privacy.erasure_completed'),
      ('trust.require_mfa'),('trust.challenge'),('trust.analyst_review'),('trust.content_reported'),
      ('finops.cost_attributed'),('finops.anomaly_detected'),
      ('runbook.incident_declared'),('runbook.incident_updated'),('runbook.alert_acknowledged')
    ON CONFLICT DO NOTHING;
  END IF;
END $$;
