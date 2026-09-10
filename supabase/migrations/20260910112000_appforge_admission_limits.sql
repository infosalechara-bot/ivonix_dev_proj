create table if not exists public.appforge_org_limits (
  organization_id uuid primary key references public.organizations(id) on delete cascade,
  max_apps integer not null default 25 check (max_apps between 1 and 1000),
  max_generated_tables integer not null default 250 check (max_generated_tables between 1 and 10000),
  max_concurrent_provisioning integer not null default 2 check (max_concurrent_provisioning between 1 and 32),
  updated_at timestamptz not null default now()
);

alter table public.appforge_org_limits enable row level security;

create index if not exists appforge_org_limits_org_idx
  on public.appforge_org_limits (organization_id);

revoke all on public.appforge_org_limits from anon, authenticated;
grant select on public.appforge_org_limits to service_role;
grant insert, update, delete on public.appforge_org_limits to service_role;
