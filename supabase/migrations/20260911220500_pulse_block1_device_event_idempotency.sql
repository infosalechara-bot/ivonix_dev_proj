create unique index if not exists events_org_external_event_id_uk
  on public.events(organization_id, external_event_id)
  where external_event_id is not null;
