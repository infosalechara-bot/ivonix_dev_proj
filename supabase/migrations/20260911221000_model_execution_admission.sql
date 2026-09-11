-- Block 2: explicit model execution admission.
-- Existing models are grandfathered as admitted to preserve current behavior.
-- Newly created models are not executable until an operator explicitly admits them.

ALTER TABLE public.core_models
  ADD COLUMN IF NOT EXISTS execution_enabled boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS admitted_at timestamptz,
  ADD COLUMN IF NOT EXISTS admitted_by uuid;

UPDATE public.core_models
   SET execution_enabled = true,
       admitted_at = COALESCE(admitted_at, created_at),
       admitted_by = COALESCE(admitted_by, created_by)
 WHERE execution_enabled = false
   AND admitted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_core_models_execution_admission
  ON public.core_models(organization_id, execution_enabled, id);

COMMENT ON COLUMN public.core_models.execution_enabled IS
  'Explicit execution admission gate. New models default to disabled; existing models are grandfathered by this migration.';
COMMENT ON COLUMN public.core_models.admitted_at IS
  'Timestamp at which model execution was admitted.';
COMMENT ON COLUMN public.core_models.admitted_by IS
  'Principal that admitted model execution; NULL is permitted for grandfathered records.';
