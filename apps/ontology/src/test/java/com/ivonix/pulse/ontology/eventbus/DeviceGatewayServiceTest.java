package com.ivonix.pulse.ontology.eventbus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceGatewayServiceTest {
    private DeviceGatewayService service() {
        return new DeviceGatewayService(mock(JdbcTemplate.class), new ObjectMapper());
    }

    @Test
    void rejectsMissingCredentialBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DeviceGatewayService service = new DeviceGatewayService(jdbc, new ObjectMapper());
        var message = message("1.0", "TELEMETRY", 1, Map.of());
        assertThrows(SecurityException.class, () -> service.ingest(message, " ", null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsUnsupportedSchemaVersionBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DeviceGatewayService service = new DeviceGatewayService(jdbc, new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> service.ingest(message("9.0", "TELEMETRY", 1, Map.of()), "token", null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsUnsupportedMessageTypeBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DeviceGatewayService service = new DeviceGatewayService(jdbc, new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> service.ingest(message("1.0", "COMMAND", 1, Map.of()), "token", null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsNegativeSequenceBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DeviceGatewayService service = new DeviceGatewayService(jdbc, new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> service.ingest(message("1.0", "TELEMETRY", -1, Map.of()), "token", null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMissingMessageIdentityBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DeviceGatewayService service = new DeviceGatewayService(jdbc, new ObjectMapper());
        var message = new DeviceGatewayService.DeviceMessage("1.0", "", UUID.randomUUID().toString(), "TELEMETRY", null, 1, Map.of(), "HTTPS", null);
        assertThrows(IllegalArgumentException.class, () -> service.ingest(message, "token", null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsOversizedPayloadBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DeviceGatewayService service = new DeviceGatewayService(jdbc, new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> service.ingest(message("1.0", "TELEMETRY", 1, Map.of("blob", "x".repeat(300_000))), "token", null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsExcessivelyNestedPayloadBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DeviceGatewayService service = new DeviceGatewayService(jdbc, new ObjectMapper());
        Map<String,Object> root = new HashMap<>();
        Map<String,Object> current = root;
        for (int i = 0; i < 20; i++) {
            Map<String,Object> next = new HashMap<>();
            current.put("next", next);
            current = next;
        }
        assertThrows(IllegalArgumentException.class, () -> service.ingest(message("1.0", "TELEMETRY", 1, root), "token", null));
        verifyNoInteractions(jdbc);
    }

    private DeviceGatewayService.DeviceMessage message(String schema, String type, long sequence, Map<String,Object> payload) {
        return new DeviceGatewayService.DeviceMessage(schema, "message-1", UUID.randomUUID().toString(), type, null, sequence, payload, "HTTPS", null);
    }
}
