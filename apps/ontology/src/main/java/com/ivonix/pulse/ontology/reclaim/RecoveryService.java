package com.ivonix.pulse.ontology.reclaim;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class RecoveryService {
    private final JdbcTemplate jdbc;
    private final RestTemplate restTemplate;
    private final String workerUrl;
    private final String workerSecret;

    public RecoveryService(JdbcTemplate jdbc, RestTemplate restTemplate,
                           @Value("${PULSE_RECLAIM_WORKER_URL:http://recovery-worker:8092}") String workerUrl,
                           @Value("${PULSE_RECLAIM_WORKER_SECRET:}") String workerSecret) {
        this.jdbc = jdbc;
        this.restTemplate = restTemplate;
        this.workerUrl = workerUrl.replaceAll("/$", "");
        this.workerSecret = workerSecret;
    }

    public UUID createRecoveryJob(RecoveryJobRequest r, UUID userId) {
        requireAdmin(r.organizationId(), userId);
        validateTarget(r);
        if (r.deviceId() != null && !deviceInOrg(r.deviceId(), r.organizationId())) throw new SecurityException("Device is not part of the organization");
        UUID jobId = UUID.randomUUID();
        jdbc.update("""
            insert into recovery_jobs
              (id, organization_id, device_id, job_type, target_path, target_table,
               target_identifier, status, created_by)
            values (?, ?, ?, ?, ?, ?, ?, 'queued', ?)
            """, jobId, r.organizationId(), r.deviceId(), r.jobType(), r.targetPath(), r.targetTable(), r.targetIdentifier(), userId);
        log(jobId, userId, "job_created", Map.of("job_type", r.jobType()));
        triggerWorker(jobId, r, userId);
        return jobId;
    }

    public List<Map<String, Object>> getJobs(UUID organizationId, UUID userId) {
        requireAdmin(organizationId, userId);
        return jdbc.queryForList("select * from recovery_jobs where organization_id = ? order by created_at desc", organizationId);
    }

    public List<Map<String, Object>> getRecoveredItems(UUID jobId, UUID userId) {
        UUID org = jobOrg(jobId); requireAdmin(org, userId);
        return jdbc.queryForList("select * from recovered_items where recovery_job_id = ? order by recovered_at desc", jobId);
    }

    public List<Map<String, Object>> getLogs(UUID jobId, UUID userId) {
        UUID org = jobOrg(jobId); requireAdmin(org, userId);
        return jdbc.queryForList("select * from recovery_logs where recovery_job_id = ? order by timestamp desc", jobId);
    }

    private UUID jobOrg(UUID jobId) {
        UUID org = jdbc.query("select organization_id from recovery_jobs where id = ?", ps -> ps.setObject(1, jobId), rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        if (org == null) throw new NoSuchElementException("Recovery job not found");
        return org;
    }

    private void requireAdmin(UUID org, UUID userId) {
        Boolean allowed = jdbc.queryForObject("select exists(select 1 from organization_members where organization_id = ? and user_id = ? and lower(role) in ('admin','owner'))", Boolean.class, org, userId);
        if (!Boolean.TRUE.equals(allowed)) throw new SecurityException("Administrator authorization required");
    }

    private boolean deviceInOrg(UUID deviceId, UUID orgId) {
        Boolean exists = jdbc.queryForObject("select exists(select 1 from devices where id = ? and organization_id = ?)", Boolean.class, deviceId, orgId);
        return Boolean.TRUE.equals(exists);
    }

    private void validateTarget(RecoveryJobRequest r) {
        switch (r.jobType()) {
            case "file", "system", "message" -> { if (r.targetPath() == null || r.targetPath().isBlank()) throw new IllegalArgumentException("targetPath is required"); }
            case "database" -> { if (r.targetTable() == null || !r.targetTable().matches("[a-zA-Z_][a-zA-Z0-9_]{0,62}")) throw new IllegalArgumentException("A safe targetTable is required"); }
        }
    }

    private void triggerWorker(UUID jobId, RecoveryJobRequest r, UUID userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", jobId.toString()); payload.put("deviceId", r.deviceId() == null ? null : r.deviceId().toString());
        payload.put("jobType", r.jobType()); payload.put("targetPath", r.targetPath()); payload.put("targetTable", r.targetTable()); payload.put("targetIdentifier", r.targetIdentifier());
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (!workerSecret.isBlank()) headers.set("X-PULSE-RECLAIM-SECRET", workerSecret);
        try { restTemplate.postForEntity(workerUrl + "/recover", new HttpEntity<>(payload, headers), String.class); }
        catch (RestClientException ex) {
            jdbc.update("update recovery_jobs set status='failed', completed_at=now(), result_summary=?::jsonb where id=?", "{\"error\":\"Recovery worker unavailable\"}", jobId);
            log(jobId, userId, "worker_dispatch_failed", Map.of("error", "Recovery worker unavailable"));
            throw new IllegalStateException("Recovery worker unavailable", ex);
        }
    }

    private void log(UUID jobId, UUID userId, String step, Map<String, Object> details) {
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(details);
            jdbc.update("insert into recovery_logs(recovery_job_id,user_id,step,details) values(?,?,?,?::jsonb)", jobId, userId, step, json);
        } catch (Exception ignored) { }
    }
}
