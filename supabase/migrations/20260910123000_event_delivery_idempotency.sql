-- PULSE Event Bus: durable delivery operation identity and idempotency.
-- The delivery row is the authoritative execution record. Downstream consumers must
-- treat operation_id as the stable idempotency key for the event/subscription pair.

ALTER TABLE public.event_deliveries
  ADD COLUMN IF NOT EXISTS operation_id uuid,
  ADD COLUMN IF NOT EXISTS completed_at timestamptz,
  ADD COLUMN IF NOT EXISTS last_error text;

UPDATE public.event_deliveries
SET operation_id = gen_random_uuid()
WHERE operation_id IS NULL;

ALTER TABLE public.event_deliveries
  ALTER COLUMN operation_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_event_deliveries_operation_id
  ON public.event_deliveries(operation_id);

CREATE INDEX IF NOT EXISTS ix_event_deliveries_event_subscription
  ON public.event_deliveries(event_id, subscription_id);

ALTER TABLE public.event_deliveries
  DROP CONSTRAINT IF EXISTS event_deliveries_operation_id_not_blank;

ALTER TABLE public.event_deliveries
  ADD CONSTRAINT event_deliveries_operation_id_not_blank
  CHECK (operation_id IS NOT NULL);

COMMENT ON COLUMN public.event_deliveries.operation_id IS
  'Stable idempotency key for exactly one event-delivery side-effect attempt lifecycle.';

COMMENT ON COLUMN public.event_deliveries.completed_at IS
  'Terminal success timestamp. Consumers must not repeat a successful operation_id.';
