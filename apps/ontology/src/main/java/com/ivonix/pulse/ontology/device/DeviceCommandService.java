package com.ivonix.pulse.ontology.device;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DeviceCommandService {
    private final JdbcTemplate jdbc;
    private final MqttIntegrationService mqtt;

    public DeviceCommandService(JdbcTemplate jdbc, MqttIntegrationService mqtt) { this.jdbc = jdbc; this.mqtt = mqtt; }

    public CommandResult issue(UUID userId, UUID organizationId, UUID deviceId, String commandType, JsonNode payload, String idempotencyKey) {
        requireMember(userId, organizationId);
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("Idempotency-Key is required");
        String normalizedKey = idempotencyKey.trim();
        requireDevice(organizationId, deviceId);

        CreationResult result = createOrGetPending(organizationId, deviceId, commandType, payload, normalizedKey);
        if (!result.created()) return new CommandResult(result.id(), result.status(), result.createdAt());

        try {
            mqtt.publishCommand(deviceId, result.id(), commandType, payload);
            markPublished(result.id(), organizationId, deviceId);
            audit(organizationId, result.id(), "command.issued");
            return new CommandResult(result.id(), "published", java.time.Instant.now());
        } catch (RuntimeException e) {
            markFailed(result.id(), organizationId, deviceId, e.getMessage());
            throw e;
        }
    }

    /**
     * Atomically claims the idempotency key. Only the transaction that actually
     * inserts the row may publish the MQTT command; concurrent callers return the
     * already-created command instead of racing into duplicate publication.
     */
    protected CreationResult createOrGetPending(UUID organizationId, UUID deviceId, String commandType, JsonNode payload, String idempotencyKey) {
        UUID id = UUID.randomUUID();
        String body = payload == null ? "{}" : payload.toString();
        return jdbc.query(
                "with inserted as (" +
                " insert into public.device_commands(id,organization_id,device_id,command_type,payload,status,idempotency_key,correlation_id,created_at)" +
                " values (?,?,?,?,?::jsonb,'pending',?,?,now())" +
                " on conflict (organization_id,idempotency_key) do nothing" +
                " returning id,status,created_at" +
                ") " +
                "select id,status,created_at,true as created from inserted " +
                "union all " +
                "select id,status,created_at,false as created from public.device_commands " +
                "where organization_id=? and idempotency_key=? " +
                "limit 1",
                rs -> {
                    if (!rs.next()) throw new IllegalStateException("Unable to resolve command idempotency key");
                    return new CreationResult(
                            (UUID) rs.getObject("id"),
                            rs.getString("status"),
                            rs.getObject("created_at", java.time.Instant.class),
                            rs.getBoolean("created"));
                },
                id, organizationId, deviceId, commandType, body, id.toString(),
                organizationId, idempotencyKey);
    }

    private void markPublished(UUID commandId, UUID organizationId, UUID deviceId) {
        int updated = jdbc.update("update public.device_commands set status='published',published_at=now() where id=? and organization_id=? and device_id=? and status='pending'", commandId, organizationId, deviceId);
        if (updated != 1) throw new IllegalStateException("Command state transition to published failed");
    }

    private void markFailed(UUID commandId, UUID organizationId, UUID deviceId, String reason) {
        jdbc.update("update public.device_commands set status='failed',failure_reason=? where id=? and organization_id=? and device_id=? and status='pending'", reason, commandId, organizationId, deviceId);
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

    private record CreationResult(UUID id, String status, java.time.Instant createdAt, boolean created) {}
    public record CommandResult(UUID commandId,String status,java.time.Instant createdAt) {}
}
