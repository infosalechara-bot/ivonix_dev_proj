-- Block 1: version the existing public.event_types registry. No reliability_* tables are created.
ALTER TABLE public.event_types ADD COLUMN IF NOT EXISTS event_version integer NOT NULL DEFAULT 1, ADD COLUMN IF NOT EXISTS envelope_version integer NOT NULL DEFAULT 1, ADD COLUMN IF NOT EXISTS compatibility text NOT NULL DEFAULT 'backward', ADD COLUMN IF NOT EXISTS deprecated_at timestamptz, ADD COLUMN IF NOT EXISTS successor text, ADD COLUMN IF NOT EXISTS compatibility_until timestamptz;
ALTER TABLE public.event_types ALTER COLUMN payload_schema SET DEFAULT '{}'::jsonb;
UPDATE public.event_types SET payload_schema='{}'::jsonb WHERE payload_schema IS NULL;
ALTER TABLE public.event_types ALTER COLUMN payload_schema SET NOT NULL;
ALTER TABLE public.event_types ADD CONSTRAINT event_types_event_version_positive CHECK(event_version>=1);
ALTER TABLE public.event_types ADD CONSTRAINT event_types_envelope_version_positive CHECK(envelope_version>=1);
ALTER TABLE public.event_types ADD CONSTRAINT event_types_compatibility_valid CHECK(compatibility IN('backward','forward','full','none'));
CREATE UNIQUE INDEX IF NOT EXISTS event_types_name_version_unique ON public.event_types(name,event_version);
CREATE INDEX IF NOT EXISTS idx_event_types_name_version ON public.event_types(name,event_version DESC);
