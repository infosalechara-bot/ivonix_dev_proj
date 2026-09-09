package com.ivonix.pulse.ontology.insight;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InsightService {
    private static final Pattern TABLE = Pattern.compile("\\b(?:from|join)\\s+([a-z_][a-z0-9_]*)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "organizations","organization_members","devices","device_telemetry","machine_dna",
            "diagnostic_procedures","security_events","ontology_types","ontology_entities",
            "ontology_relationships","find_registrations","finder_services","find_searches","find_matches",
            "threat_indicators","malware_detections","security_incidents","ai_datasets","ai_models",
            "ai_training_jobs","ai_deployments","app_definitions","app_generated_tables","reports",
            "report_results","nl_query_history"
    );

    private final JdbcTemplate jdbc;
    private final RestTemplate rest;
    private final ObjectMapper mapper;
    private final String nlpUrl;

    public InsightService(JdbcTemplate jdbc, RestTemplate rest, ObjectMapper mapper,
                          @Value("${PULSE_NL_TO_SQL_URL:http://127.0.0.1:8091}") String nlpUrl) {
        this.jdbc = jdbc; this.rest = rest; this.mapper = mapper; this.nlpUrl = nlpUrl;
    }

    public UUID createReport(UUID org, UUID user, ReportRequest r) {
        if (!Set.of("sql","natural_language","dashboard").contains(r.queryType())) throw new IllegalArgumentException("Unsupported query type");
        String definition = r.queryDefinition() == null ? "" : r.queryDefinition().trim();
        if (definition.isBlank()) throw new IllegalArgumentException("Query definition is required");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into reports(id,organization_id,name,description,query_type,query_definition,created_by) values (?,?,?,?,?,?::jsonb,?)",
                id, org, r.name(), r.description(), r.queryType(), mapper.writeValueAsString(Map.of(r.queryType().equals("natural_language") ? "text" : r.queryType().equals("sql") ? "sql" : "config", definition)), user);
        return id;
    }

    public List<Map<String,Object>> listReports(UUID org, UUID user) {
        requireMember(org, user);
        return jdbc.queryForList("select id,name,description,query_type,query_definition,created_at,updated_at from reports where organization_id=? order by updated_at desc", org);
    }

    public List<Map<String,Object>> execute(UUID reportId, UUID org, UUID user) {
        requireMember(org, user);
        Map<String,Object> r = jdbc.queryForMap("select query_type,query_definition from reports where id=? and organization_id=?", reportId, org);
        String type = String.valueOf(r.get("query_type"));
        String definition = extractDefinition(r.get("query_definition"), type);
        String sql = type.equals("natural_language") ? convertNaturalLanguage(definition, org) : definition;
        sql = validateAndScope(sql, org);
        List<Map<String,Object>> rows = jdbc.queryForList(sql);
        try {
            jdbc.update("insert into report_results(report_id,organization_id,result_data,executed_by) values (?,?,?::jsonb,?)",
                    reportId, org, mapper.writeValueAsString(rows), user);
        } catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize report result", e); }
        return rows;
    }

    public List<Map<String,Object>> results(UUID reportId, UUID org, UUID user) {
        requireMember(org, user);
        return jdbc.queryForList("select id,result_data,executed_at,executed_by from report_results where report_id=? and organization_id=? order by executed_at desc limit 10", reportId, org);
    }

    private String convertNaturalLanguage(String text, UUID org) {
        Map<String,String> body = Map.of("text", text, "organization_id", org.toString());
        Map<?,?> response = rest.postForObject(nlpUrl + "/convert", body, Map.class);
        if (response == null || response.get("sql") == null) throw new IllegalStateException("NL-to-SQL service returned no SQL");
        return String.valueOf(response.get("sql"));
    }

    private String validateAndScope(String sql, UUID org) {
        String s = sql.trim();
        String lower = s.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("select ") || lower.startsWith("select\n") || lower.startsWith("with "))) throw new IllegalArgumentException("Only SELECT queries are allowed");
        if (s.contains(";") || s.contains("--") || s.contains("/*") || s.contains("*/") || lower.contains("pg_catalog") || lower.contains("information_schema")) throw new IllegalArgumentException("Unsafe SQL rejected");
        Matcher m = TABLE.matcher(s);
        boolean found = false;
        while (m.find()) {
            found = true;
            String table = m.group(1).toLowerCase(Locale.ROOT);
            if (!ALLOWED_TABLES.contains(table)) throw new IllegalArgumentException("Table is not allowed: " + table);
        }
        if (!found) throw new IllegalArgumentException("Query must reference an allowed PULSE table");
        // Every org-scoped query must explicitly constrain organization_id. This prevents cross-tenant reads.
        if (!lower.contains("organization_id")) throw new IllegalArgumentException("Query must include organization_id scope");
        String scoped = s.replaceAll("(?i)\\borganization_id\\s*=\\s*'?[0-9a-f-]{36}'?", "organization_id = '" + org + "'");
        if (!scoped.toLowerCase(Locale.ROOT).contains("organization_id = '" + org.toString().toLowerCase(Locale.ROOT) + "'")) throw new IllegalArgumentException("Organization scope must match authenticated organization");
        return scoped;
    }

    private String extractDefinition(Object json, String type) {
        String raw = String.valueOf(json);
        if (raw.startsWith("{") && raw.contains("\"")) {
            try {
                Map<?,?> m = mapper.readValue(raw, Map.class);
                Object v = m.get(type.equals("natural_language") ? "text" : type.equals("sql") ? "sql" : "config");
                if (v != null) return String.valueOf(v);
            } catch (Exception ignored) { }
        }
        return raw;
    }

    private void requireMember(UUID org, UUID user) {
        Integer n = jdbc.queryForObject("select count(*) from organization_members where organization_id=? and user_id=?", Integer.class, org, user);
        if (n == null || n != 1) throw new SecurityException("Organization membership required");
    }

    public record ReportRequest(String name, String description, String queryType, String queryDefinition) {}
}
