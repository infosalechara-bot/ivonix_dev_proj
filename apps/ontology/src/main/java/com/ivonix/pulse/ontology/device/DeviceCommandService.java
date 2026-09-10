package com.ivonix.pulse.ontology.device;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DeviceCommandService {
    private final JdbcTemplate jdbc;
    private final MqttIntegrationService mqtt;

    public DeviceCommandService(JdbcTemplate jdbc, MqttIntegrationService mqtt) { this.jdbc = jdbc; this.mqtt = mqtt; }

    public CommandResult issue(UUID userId, UUID organizationId, UUID deviceId, String commandType, JsonNode payload, String idempotencyKey) {
        requireMember(userId, organizationId);
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency-Key is required");
        Existing existing = findExisting(organizationId, idempotencyKey.trim());
        if (existing != null) return new CommandResult(existing.id(), existing.status(), existing.createdAt());
        UUID commandId = createPending(organizationId, deviceId, commandType, payload, idempotencyKey.trim());
        try {
            mqtt.publishCommand(deviceId, commandId, commandType, payload);
            markPublished(commandId, organizationId, deviceId);
            audit(organizationId, commandId, "command.issued");
            return new CommandResult(commandId, "published", java.time.Instant.now());
        } catch (RuntimeException e) {
            markFailed(commandId, organizationId, deviceId, e.getMessage());
            throw e;
        }
    }

    @Transactional
    protected UUID createPending(UUID organizationId, UUID deviceId, String commandType, JsonNode payload, String idempotencyKey) {
        requireDevice(organizationId, deviceId);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into public.device_commands(id,organization_id,device_id,command_type,payload,status,idempotency_key,correlation_id,created_at) values (?,?,?,?,?::jsonb,'pending',?,?,now())",
                id, organizationId, deviceId, commandType, payload == null ? "{}" : payload.toString(), idempotencyKey, id.toString());
        return id;
    }

    private void markPublished(UUID commandId, UUID organizationId, UUID deviceId) {
        int updated = jdbc.update("update public.device_commands set status='published',published_at=now() where id=? and organization_id=? and device_id=? and status='pending'", commandId, organizationId, deviceId);
        if (updated != 1) throw new IllegalStateException("Command state transition to published failed");
    }

    private void markFailed(UUID commandId, UUID organizationId, UUID deviceId, String reason) {
        jdbc.update("update public.device_commands set status='failed',failure_reason=? where id=? and organization_id=? and device_id=? and status='pending'", reason, commandId, organizationId, deviceId);
    }

    private Existing findExisting(UUID organizationId, String idempotencyKey) {
        return jdbc.query("select id,status,created_at from public.device_commands where organization_id=? and idempotency_key=?", rs -> rs.next() ? new Existing((UUID)rs.getObject("id"), rs.getString("status"), rs.getObject("created_at", java.time.Instant.class)) : null, organizationId, idempotencyKey);
    }

    private void requireDevice(UUID organizationId, UUID deviceId) {
        Boolean exists = jdbc.queryForObject("select exists(select 1 from public.devices where id=? and organization_id=?)", Boolean.class, deviceId, organizationId);
        if (!Boolean.TRUE.equals(exists)) throw new SecurityException("Device is not in organization");
    }

    private void requireMember(UUID userId, UUID organizationId) {
        Integer count = jdbc.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?", Integer.class, organizationId, userId);
        if (count == null || count < 1) throw new SecurityException("Organization membership required");
    }

    private void audit(UUID organizationId, UUID commandId, String action) {
        jdbc.update("insert into public.audit_logs(user_id,action,resource_type,resource_id,created_at) values (?,?,?,?,now())", null, action, "device_command", commandId.toString());
    }

    private record Existing(UUID id,String status,java.time.Instant createdAt) {}
    public record CommandResult(UUID commandId,String status,java.time.Instant createdAt) {}
}
