-- PULSE foundation security hardening. Safe to re-run with IF EXISTS policy drops.
ALTER FUNCTION public.is_org_member(uuid) SECURITY INVOKER SET search_path = public, pg_catalog;
REVOKE EXECUTE ON FUNCTION public.is_org_member(uuid) FROM anon;
GRANT EXECUTE ON FUNCTION public.is_org_member(uuid) TO authenticated;
REVOKE EXECUTE ON FUNCTION public.rls_auto_enable() FROM PUBLIC, anon, authenticated;
ALTER FUNCTION public.get_entity_graph(uuid, integer, uuid) SET search_path = public, pg_catalog;
ALTER FUNCTION public.set_updated_at() SET search_path = public, pg_catalog;
ALTER FUNCTION public.set_investigation_case_updated_at() SET search_path = public, pg_catalog;
ALTER FUNCTION public.common_neighbor_count(uuid, uuid, uuid) SET search_path = public, pg_catalog;
ALTER FUNCTION public.graph_shortest_path(uuid, uuid, uuid) SET search_path = public, pg_catalog;
ALTER FUNCTION public.touch_ledger_contract_updated_at() SET search_path = public, pg_catalog;

DROP POLICY IF EXISTS audit_logs_self_select ON public.audit_logs;
CREATE POLICY audit_logs_self_select ON public.audit_logs FOR SELECT TO authenticated USING (user_id = (select auth.uid()));
DROP POLICY IF EXISTS machine_dna_org_select ON public.machine_dna;
CREATE POLICY machine_dna_org_select ON public.machine_dna FOR SELECT TO authenticated USING (EXISTS (SELECT 1 FROM public.devices d WHERE d.id = machine_dna.device_id AND public.is_org_member(d.organization_id)));
DROP POLICY IF EXISTS diagnostic_procedures_org_select ON public.diagnostic_procedures;
CREATE POLICY diagnostic_procedures_org_select ON public.diagnostic_procedures FOR SELECT TO authenticated USING (EXISTS (SELECT 1 FROM public.machine_dna md JOIN public.devices d ON d.id = md.device_id WHERE md.id = diagnostic_procedures.machine_dna_id AND public.is_org_member(d.organization_id)));
DROP POLICY IF EXISTS finder_services_authenticated_select ON public.finder_services;
CREATE POLICY finder_services_authenticated_select ON public.finder_services FOR SELECT TO authenticated USING (true);
DROP POLICY IF EXISTS ontology_types_authenticated_select ON public.ontology_types;
CREATE POLICY ontology_types_authenticated_select ON public.ontology_types FOR SELECT TO authenticated USING (true);
DROP POLICY IF EXISTS refresh_tokens_self_select ON public.refresh_tokens;
CREATE POLICY refresh_tokens_self_select ON public.refresh_tokens FOR SELECT TO authenticated USING (user_id = (select auth.uid()));

ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.diagnostic_procedures ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.finder_services ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.machine_dna ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.ontology_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.refresh_tokens ENABLE ROW LEVEL SECURITY;
