package com.ivonix.pulse.ontology.eventbus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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
        var message = message("1.0", "TELEMETRY", 1);
        assertThrows(SecurityException.class, () -> service.ingest(message, " ", null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsUnsupportedSchemaVersionBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DeviceGatewayService service = new DeviceGatewayService(jdbc, new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> service.ingest(message("9.0", "TELEMETRY", 1), "token", null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsUnsupportedMessageTypeBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DeviceGatewayService service = new DeviceGatewayService(jdbc, new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> service.ingest(message("1.0", "COMMAND", 1), "token", null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsNegativeSequenceBeforeDatabaseAccess() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DeviceGatewayService service = new DeviceGatewayService(jdbc, new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> service.ingest(message("1.0", "TELEMETRY", -1), "token", null));
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

    private DeviceGatewayService.DeviceMessage message(String schema, String type, long sequence) {
        return new DeviceGatewayService.DeviceMessage(schema, "message-1", UUID.randomUUID().toString(), type, null, sequence, Map.of(), "HTTPS", null);
    }
}
