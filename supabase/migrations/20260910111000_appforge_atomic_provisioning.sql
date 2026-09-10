alter table public.app_definitions
  add column if not exists provisioning_status text not null default 'READY';

alter table public.app_definitions
  add constraint app_definitions_provisioning_status_ck
  check (provisioning_status in ('REQUESTED','PROVISIONING','READY','FAILED'));

create index if not exists app_definitions_org_status_idx
  on public.app_definitions (organization_id, provisioning_status);
