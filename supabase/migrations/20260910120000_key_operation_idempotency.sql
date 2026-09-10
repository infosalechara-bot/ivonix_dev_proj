-- PULSE KEY operation idempotency and durable result replay.
-- Production application requires controlled migration approval.
alter table public.crypto_operations
  add column if not exists operation_id uuid,
  add column if not exists result_ciphertext text,
  add column if not exists result_plaintext text,
  add column if not exists completed_at timestamptz;

create unique index if not exists ux_crypto_operations_operation_id
  on public.crypto_operations (operation_id)
  where operation_id is not null;

create index if not exists idx_crypto_operations_org_created
  on public.crypto_operations (organization_id, created_at desc);
