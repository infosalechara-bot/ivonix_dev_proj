package com.ivonix.pulse.ontology.device;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.MultiValueMap;

import java.util.*;

@RestController
public class DeviceCommandController {
    private final DeviceCommandService commands;
    private final MqttIntegrationService mqtt;

    public DeviceCommandController(DeviceCommandService commands, MqttIntegrationService mqtt) { this.commands = commands; this.mqtt = mqtt; }

    @PostMapping("/api/v1/device-commands")
    public ResponseEntity<?> issue(Authentication authentication,
                                   @RequestBody CommandRequest request,
                                   @RequestHeader("Idempotency-Key") String idempotencyKey) {
        UUID userId = UUID.fromString(authentication.getName());
        UUID organizationId = UUID.fromString(request.organizationId());
        UUID deviceId = UUID.fromString(request.deviceId());
        return ResponseEntity.accepted().body(commands.issue(userId, organizationId, deviceId, request.commandType(), request.payload(), idempotencyKey));
    }

    @PostMapping(value="/api/v1/mqtt/auth", consumes={"application/json","application/x-www-form-urlencoded"})
    public ResponseEntity<?> mqttAuth(@RequestParam MultiValueMap<String,String> params,
                                      @RequestBody(required=false) Map<String,Object> body) {
        String username = first(params,"username",body);
        String password = first(params,"password",body);
        if (username == null || password == null) return ResponseEntity.ok(Map.of("result","deny","is_superuser",false));
        try { return ResponseEntity.ok(mqtt.authenticate(username,password)); }
        catch (RuntimeException e) { return ResponseEntity.ok(Map.of("result","deny","is_superuser",false)); }
    }

    @PostMapping(value="/api/v1/mqtt/webhook", consumes="application/json")
    public ResponseEntity<?> mqttWebhook(@RequestHeader(value="X-PULSE-MQTT-WEBHOOK-SECRET",required=false) String secret,
                                         @RequestBody JsonNode body) {
        if (!mqtt.webhookSecretMatches(secret)) return ResponseEntity.status(401).body(Map.of("error","unauthorized"));
        mqtt.handleWebhook(body);
        return ResponseEntity.ok(Map.of("result","ok"));
    }

    private String first(MultiValueMap<String,String> params, String key, Map<String,Object> body) {
        String value=params.getFirst(key); if(value!=null&&!value.isBlank()) return value;
        if(body==null)return null; Object v=body.get(key); return v==null?null:String.valueOf(v);
    }

    public record CommandRequest(String organizationId,String deviceId,String commandType,JsonNode payload) {}
}
