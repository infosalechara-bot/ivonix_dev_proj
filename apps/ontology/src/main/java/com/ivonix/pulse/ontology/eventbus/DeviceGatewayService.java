package com.ivonix.pulse.ontology.eventbus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Authenticated machine ingress. The device credential determines tenant ownership; callers cannot choose organization_id. */
@Service
public class DeviceGatewayService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public DeviceGatewayService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Transactional
    public IngestResult ingest(DeviceMessage message, String credential, String headerDeviceId) {
        if (credential == null || credential.isBlank()) throw new SecurityException("Device credential required");
        if (message == null || message.messageId() == null || message.messageId().isBlank()) throw new IllegalArgumentException("messageId required");
        if (message.deviceId() == null || message.deviceId().isBlank()) throw new IllegalArgumentException("deviceId required");
        if (message.sequence() < 0) throw new IllegalArgumentException("sequence must be non-negative");
        if (!"1.0".equals(message.schemaVersion())) throw new IllegalArgumentException("Unsupported device message schema version");
        if (!Set.of("TELEMETRY", "HEARTBEAT", "STATE", "ALARM").contains(message.messageType())) throw new IllegalArgumentException("Unsupported device message type");

        DeviceIdentity device = authenticate(credential);
        String expected = device.deviceId().toString();
        if (!expected.equals(message.deviceId()) || (headerDeviceId != null && !expected.equals(headerDeviceId))) throw new SecurityException("Device identity mismatch");

        // Atomically reject replays and out-of-order messages. NULL means no message has been accepted yet.
        int advanced = jdbc.update("update public.key_devices set last_sequence=?,last_seen_at=now() where id=? and status='active' and credential_expires_at>now() and (last_sequence is null or last_sequence < ?)", message.sequence(), device.deviceId(), message.sequence());
        if (advanced != 1) {
            UUID existing = findExisting(device.organizationId(), message.messageId());
            if (existing != null) return new IngestResult(existing, device.deviceId(), device.organizationId(), true, "duplicate");
            throw new SecurityException("Replay or out-of-order device message");
        }

        UUID eventType = jdbc.queryForObject("select id from public.event_types where name=?", UUID.class, "device_telemetry_received");
        UUID eventId = UUID.randomUUID();
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", message.schemaVersion()); payload.put("messageId", message.messageId()); payload.put("deviceId", message.deviceId()); payload.put("messageType", message.messageType()); payload.put("sentAt", message.sentAt()); payload.put("sequence", message.sequence()); payload.put("payload", message.payload() == null ? Map.of() : message.payload()); payload.put("protocol", message.protocol()); payload.put("keyId", message.keyId());
        try {
            jdbc.update("insert into public.events(id,event_type_id,organization_id,source_service,event_time,payload,correlation_id,version,external_event_id) values (?,?,?,?,?::timestamptz,?::jsonb,?,?,?)", eventId, eventType, device.organizationId(), "device-gateway", message.sentAt() == null ? Instant.now() : Instant.parse(message.sentAt()), mapper.writeValueAsString(payload), message.messageId(), 1, message.messageId());
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            UUID existing = findExisting(device.organizationId(), message.messageId());
            if (existing != null) return new IngestResult(existing, device.deviceId(), device.organizationId(), true, "duplicate");
            throw duplicate;
        } catch (JsonProcessingException | java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid device message payload or timestamp", e);
        }
        return new IngestResult(eventId, device.deviceId(), device.organizationId(), false, "accepted");
    }

    private DeviceIdentity authenticate(String credential) {
        try {
            return jdbc.queryForObject("select id,organization_id from public.key_devices where credential_hash=? and status='active' and credential_expires_at>now()", (rs,n) -> new DeviceIdentity((UUID)rs.getObject("id"), (UUID)rs.getObject("organization_id")), sha256(credential.trim()));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) { throw new SecurityException("Invalid or expired device credential"); }
    }
    private UUID findExisting(UUID organizationId, String messageId) { try { return jdbc.queryForObject("select id from public.events where organization_id=? and external_event_id=?", UUID.class, organizationId, messageId); } catch (org.springframework.dao.EmptyResultDataAccessException e) { return null; } }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("Credential hashing unavailable", e); } }
    public record DeviceMessage(String schemaVersion, String messageId, String deviceId, String messageType, String sentAt, long sequence, Map<String,Object> payload, String protocol, String keyId) {}
    public record IngestResult(UUID eventId, UUID deviceId, UUID organizationId, boolean duplicate, String status) {}
    private record DeviceIdentity(UUID deviceId, UUID organizationId) {}
}
