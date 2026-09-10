package com.ivonix.pulse.ontology.eventbus;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/device-gateway")
public class DeviceGatewayController {
    private final DeviceGatewayService service;

    public DeviceGatewayController(DeviceGatewayService service) {
        this.service = service;
    }

    /** Device credentials are separate from human JWTs and never expose tenant selection to the device. */
    @PostMapping("/messages")
    public ResponseEntity<?> ingest(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestHeader(value = "X-PULSE-Device-ID", required = false) String headerDeviceId,
                                    @RequestBody DeviceGatewayService.DeviceMessage message) {
        String credential = bearer(authorization);
        return ResponseEntity.accepted().body(service.ingest(message, credential, headerDeviceId));
    }

    private String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw new SecurityException("Device bearer credential required");
        String token = authorization.substring(7).trim();
        if (token.isBlank()) throw new SecurityException("Device bearer credential required");
        return token;
    }
}
