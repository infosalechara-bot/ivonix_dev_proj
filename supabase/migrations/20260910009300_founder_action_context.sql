-- FND-026 context binding and single-use signed receipt metadata.
-- Staged migration: production application requires controlled deployment approval.
alter table public.executive_confirmations
  add column if not exists approved_action_hash text,
  add column if not exists consumed_action text,
  add column if not exists consumed_resource_type text,
  add column if not exists consumed_resource_id text,
  add column if not exists consumption_receipt_hash text;

create index if not exists executive_confirmations_context_idx
  on public.executive_confirmations (organization_id, requested_by, status, expires_at);

alter table public.executive_confirmations enable row level security;

comment on column public.executive_confirmations.approved_action_hash is
  'SHA-256 of organization|action|resource_type|resource_id captured when approval is granted.';
comment on column public.executive_confirmations.consumption_receipt_hash is
  'SHA-256 receipt binding the consumed approval to confirmation, context and founder identity.';
