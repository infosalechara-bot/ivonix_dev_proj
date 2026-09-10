create table if not exists public.device_commands (
  id uuid primary key,
  organization_id uuid not null references public.organizations(id) on delete cascade,
  device_id uuid not null references public.devices(id) on delete cascade,
  command_type text not null,
  payload jsonb not null default '{}'::jsonb,
  status text not null default 'pending' check (status in ('pending','published','acknowledged','failed','expired')),
  correlation_id text,
  idempotency_key text,
  created_at timestamptz not null default now(),
  published_at timestamptz,
  acknowledged_at timestamptz,
  ack_payload jsonb,
  failure_reason text,
  constraint device_commands_org_idempotency_uk unique (organization_id, idempotency_key)
);

create index if not exists device_commands_device_status_idx
  on public.device_commands(device_id,status,created_at desc);
create index if not exists device_commands_org_created_idx
  on public.device_commands(organization_id,created_at desc);

alter table public.device_commands enable row level security;

create policy device_commands_org_member_select
  on public.device_commands for select
  using (exists (
    select 1 from public.organization_members m
    where m.organization_id = device_commands.organization_id
      and m.user_id = auth.uid()
  ));

create policy device_commands_org_member_insert
  on public.device_commands for insert
  with check (exists (
    select 1 from public.organization_members m
    where m.organization_id = device_commands.organization_id
      and m.user_id = auth.uid()
  ));
