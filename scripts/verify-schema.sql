-- Read-only production/staging schema gate.
-- Run against a controlled database before applying any PULSE migration.

WITH expected(table_name) AS (VALUES
 ('academy_courses'),('academy_modules'),('academy_lessons'),('academy_enrollments'),
 ('academy_lesson_progress'),('academy_quizzes'),('academy_quiz_attempts'),('academy_certificates'),
 ('i18n_locales'),('i18n_translations'),('i18n_user_prefs'),('a11y_audits'),
 ('meet_rooms'),('meet_participants'),('meet_recordings'),('meet_transcripts'),('meet_signals'),
 ('privacy_consents'),('privacy_dsar_requests'),('privacy_data_lineage'),('privacy_erasure_jobs'),
 ('trust_signals'),('trust_risk_profiles'),('trust_rules'),('trust_blocks'),('trust_content_reports'),
 ('finops_cost_events'),('finops_rate_card'),('finops_tenant_profitability'),('finops_anomalies'),
 ('runbook_teams'),('runbook_schedules'),('runbook_incidents'),('runbook_timeline'),('runbook_alerts'))
SELECT e.table_name,
       CASE WHEN t.tablename IS NULL THEN 'MISSING' ELSE 'PRESENT' END AS status,
       CASE WHEN t.tablename IS NOT NULL THEN c.relrowsecurity ELSE NULL END AS rls_enabled
FROM expected e
LEFT JOIN pg_tables t ON t.schemaname='public' AND t.tablename=e.table_name
LEFT JOIN pg_class c ON c.relname=e.table_name AND c.relnamespace='public'::regnamespace
ORDER BY e.table_name;

SELECT count(*) FILTER (WHERE relrowsecurity) AS rls_enabled,
       count(*) AS public_tables,
       count(*) FILTER (WHERE NOT relrowsecurity) AS rls_missing
FROM pg_class
WHERE relkind='r' AND relnamespace='public'::regnamespace;

SELECT schemaname, tablename, policyname, cmd, roles
FROM pg_policies
WHERE schemaname='public'
ORDER BY tablename, policyname;
