-- Block 1 corrective convergence: align the live event_types registry with the frozen contract.
ALTER TABLE public.event_types
  ADD COLUMN IF NOT EXISTS envelope_version integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS compatibility text NOT NULL DEFAULT 'backward',
  ADD COLUMN IF NOT EXISTS successor text;

UPDATE public.event_types
SET payload_schema = '{}'::jsonb
WHERE payload_schema IS NULL;
ALTER TABLE public.event_types
  ALTER COLUMN payload_schema SET DEFAULT '{}'::jsonb,
  ALTER COLUMN payload_schema SET NOT NULL;

UPDATE public.event_types
SET schema_id = 'pulse.event.' || name || '.v' || event_version
WHERE schema_id IS NULL;
ALTER TABLE public.event_types
  ALTER COLUMN schema_id SET NOT NULL;

ALTER TABLE public.event_types DROP CONSTRAINT IF EXISTS event_types_name_key;
ALTER TABLE public.event_types ADD CONSTRAINT event_types_envelope_version_positive CHECK (envelope_version >= 1);
ALTER TABLE public.event_types ADD CONSTRAINT event_types_compatibility_valid CHECK (compatibility IN ('backward','forward','full','none'));
CREATE UNIQUE INDEX IF NOT EXISTS event_types_name_version_unique ON public.event_types(name, event_version);
CREATE UNIQUE INDEX IF NOT EXISTS event_types_schema_id_unique ON public.event_types(schema_id);
CREATE INDEX IF NOT EXISTS idx_event_types_name_version ON public.event_types(name, event_version DESC);
