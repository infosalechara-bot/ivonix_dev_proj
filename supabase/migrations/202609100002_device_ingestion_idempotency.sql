-- PULSE device ingestion: replay protection and event idempotency.
-- Safe to re-run; existing rows receive NULL sequence / external event id.
ALTER TABLE public.key_devices
  ADD COLUMN IF NOT EXISTS last_sequence bigint;

ALTER TABLE public.events
  ADD COLUMN IF NOT EXISTS external_event_id text;

CREATE UNIQUE INDEX IF NOT EXISTS events_org_external_event_id_uidx
  ON public.events (organization_id, external_event_id)
  WHERE external_event_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS key_devices_org_status_idx
  ON public.key_devices (organization_id, status);

CREATE INDEX IF NOT EXISTS events_org_external_event_idx
  ON public.events (organization_id, external_event_id)
  WHERE external_event_id IS NOT NULL;

-- Device credentials are operational secrets and must never be exposed to anonymous clients.
ALTER TABLE public.key_devices ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS key_devices_org_member_select ON public.key_devices;
CREATE POLICY key_devices_org_member_select
  ON public.key_devices FOR SELECT TO authenticated
  USING (public.is_org_member(organization_id));

DROP POLICY IF EXISTS key_devices_org_member_update ON public.key_devices;
CREATE POLICY key_devices_org_member_update
  ON public.key_devices FOR UPDATE TO authenticated
  USING (public.is_org_member(organization_id))
  WITH CHECK (public.is_org_member(organization_id));
