-- PULSE Block 1: one authoritative event registry.
-- Reuses public.event_types; do not create a competing reliability_schemas table.
DO $$
BEGIN
  IF to_regclass('public.event_types') IS NULL THEN
    RAISE EXCEPTION 'PULSE contract registry requires public.event_types';
  END IF;
END $$;

ALTER TABLE public.event_types
  ADD COLUMN IF NOT EXISTS event_version integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS schema_id text,
  ADD COLUMN IF NOT EXISTS schema_status text NOT NULL DEFAULT 'active',
  ADD COLUMN IF NOT EXISTS deprecated_at timestamptz,
  ADD COLUMN IF NOT EXISTS compatibility_until timestamptz;

ALTER TABLE public.event_types
  DROP CONSTRAINT IF EXISTS event_types_event_version_positive;
ALTER TABLE public.event_types
  ADD CONSTRAINT event_types_event_version_positive CHECK (event_version >= 1);

ALTER TABLE public.event_types
  DROP CONSTRAINT IF EXISTS event_types_schema_status_valid;
ALTER TABLE public.event_types
  ADD CONSTRAINT event_types_schema_status_valid CHECK (schema_status IN ('active','deprecated','retired'));

UPDATE public.event_types
SET schema_id = 'pulse.event.' || replace(name, '.', '_') || '.v' || event_version
WHERE schema_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS event_types_name_version_uq
  ON public.event_types(name, event_version);
CREATE UNIQUE INDEX IF NOT EXISTS event_types_schema_id_uq
  ON public.event_types(schema_id)
  WHERE schema_id IS NOT NULL;

COMMENT ON TABLE public.event_types IS 'PULSE authoritative event type/schema registry. Versioned event contracts are registered here; no competing event schema registry is permitted.';
COMMENT ON COLUMN public.event_types.payload_schema IS 'JSON Schema for the event payload, not the universal PulseEvent envelope.';
COMMENT ON COLUMN public.event_types.event_version IS 'Semantic contract version of this event type. Consumers must reject unsupported versions.';
COMMENT ON COLUMN public.event_types.compatibility_until IS 'Optional end of the N-1 compatibility window; target default policy is 90 days after replacement.';
