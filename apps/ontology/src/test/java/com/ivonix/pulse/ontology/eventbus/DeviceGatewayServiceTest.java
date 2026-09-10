package com.ivonix.pulse.ontology.eventbus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceGatewayServiceTest {
    @Mock JdbcTemplate jdbc;

    private DeviceGatewayService service() {
        return new DeviceGatewayService(jdbc, new ObjectMapper());
    }

    @Test
    void rejectsInvalidCredentialBeforeTouchingEventPath() {
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        var message = new DeviceGatewayService.DeviceMessage("1.0", "m-1", UUID.randomUUID().toString(), "TELEMETRY", null, 1, Map.of(), "HTTPS", null);
        assertThrows(SecurityException.class, () -> service().ingest(message, "bad", null));
        verify(jdbc, never()).update(contains("public.events"), any());
    }

    @Test
    void rejectsIdentityMismatch() {
        UUID authenticated = UUID.randomUUID();
        UUID claimed = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(authenticatedIdentity(authenticated));
        var message = new DeviceGatewayService.DeviceMessage("1.0", "m-1", claimed.toString(), "TELEMETRY", null, 1, Map.of(), "HTTPS", null);
        assertThrows(SecurityException.class, () -> service().ingest(message, "token", null));
    }

    @Test
    void rejectsReplayWhenSequenceCannotAdvance() {
        UUID device = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(authenticatedIdentity(device));
        when(jdbc.update(contains("last_sequence"), any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(contains("external_event_id"), eq(UUID.class), any(Object[].class)))
                .thenThrow(new EmptyResultDataAccessException(1));
        var message = new DeviceGatewayService.DeviceMessage("1.0", "m-1", device.toString(), "TELEMETRY", null, 4, Map.of(), "HTTPS", null);
        assertThrows(SecurityException.class, () -> service().ingest(message, "token", null));
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        var message = new DeviceGatewayService.DeviceMessage("9.0", "m-1", UUID.randomUUID().toString(), "TELEMETRY", null, 1, Map.of(), "HTTPS", null);
        assertThrows(IllegalArgumentException.class, () -> service().ingest(message, "token", null));
        verifyNoInteractions(jdbc);
    }

    private DeviceGatewayService.DeviceMessage authenticatedIdentity(UUID device) {
        // Not used as a message; this helper is intentionally replaced by the reflection-free RowMapper answer below.
        return null;
    }

    private Object authenticatedIdentity(UUID device) {
        return new Object() {
            @SuppressWarnings("unused") UUID deviceId() { return device; }
            @SuppressWarnings("unused") UUID organizationId() { return UUID.randomUUID(); }
        };
    }

    private String sha256(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
