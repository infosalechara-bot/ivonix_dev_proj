-- Block 1: keep historical vocabulary rows if present, but prevent them from being active emission targets.
UPDATE public.event_types
SET schema_status = 'retired',
    deprecated_at = COALESCE(deprecated_at, now())
WHERE name IN (
  'privacy.consent_recorded','privacy.dsar_submitted','privacy.erasure_completed',
  'trust.require_mfa','trust.challenge','trust.analyst_review','trust.content_reported',
  'finops.cost_attributed','finops.anomaly_detected',
  'runbook.incident_declared','runbook.incident_updated','runbook.alert_acknowledged'
)
AND schema_status <> 'retired';
