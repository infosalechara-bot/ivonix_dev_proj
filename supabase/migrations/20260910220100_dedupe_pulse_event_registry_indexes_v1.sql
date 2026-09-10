-- Remove duplicate indexes left by earlier registry convergence.
-- The canonical non-partial unique indexes are retained.
DROP INDEX IF EXISTS public.event_types_name_version_uq;
DROP INDEX IF EXISTS public.event_types_schema_id_uq;
