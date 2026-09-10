-- Staged only. Production application requires controlled migration approval.
alter table public.crypto_operations
  add column if not exists organization_id uuid,
  add column if not exists caller text,
  add column if not exists operation_id uuid,
  add column if not exists result_ciphertext text,
  add column if not exists result_plaintext text;

create index if not exists idx_crypto_operations_org_created
  on public.crypto_operations (organization_id, created_at desc);

create unique index if not exists uq_crypto_operations_operation_id
  on public.crypto_operations (operation_id)
  where operation_id is not null;

alter table public.crypto_operations enable row level security;

create policy crypto_operations_member_read
  on public.crypto_operations
  for select
  to authenticated
  using (
    organization_id is not null
    and public.is_org_member(organization_id)
  );
