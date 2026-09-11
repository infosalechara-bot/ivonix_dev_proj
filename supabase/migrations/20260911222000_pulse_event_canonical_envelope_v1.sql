-- Block 1: retain the legacy event row identity while persisting the canonical
-- PULSE Universal Event Envelope alongside it. This is additive and preserves
-- existing event consumers during the contract transition.
alter table public.events
  add column if not exists canonical_envelope jsonb;

alter table public.events
  drop constraint if exists events_canonical_envelope_object_check;

alter table public.events
  add constraint events_canonical_envelope_object_check
  check (canonical_envelope is null or jsonb_typeof(canonical_envelope) = 'object');

create index if not exists events_canonical_envelope_event_id_idx
  on public.events ((canonical_envelope->>'eventId'))
  where canonical_envelope is not null;
