package com.ivonix.pulse.ontology.key;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/key")
public class UniversalKeyController {
    private final UniversalKeyService service;
    private final KeyService keys;
    public UniversalKeyController(UniversalKeyService service, KeyService keys) { this.service = service; this.keys = keys; }

    @GetMapping("/capabilities")
    public ResponseEntity<?> capabilities() { return ResponseEntity.ok(service.capabilities()); }

    @PostMapping("/activation-codes")
    public ResponseEntity<?> createActivationCode(Authentication a, @RequestBody ActivationRequest r) {
        UUID user = UUID.fromString(a.getName());
        UUID org = UUID.fromString(r.organizationId());
        UUID key = r.keyId() == null || r.keyId().isBlank() ? null : UUID.fromString(r.keyId());
        return ResponseEntity.ok(service.createActivationCode(user, org, key, r.ttlMinutes() == null ? 15 : r.ttlMinutes(), r.maxUses() == null ? 1 : r.maxUses(), r.scopes()));
    }

    @PostMapping("/activate")
    public ResponseEntity<?> activate(@RequestBody ActivationPayload r) {
        return ResponseEntity.ok(service.activate(r.code(), r.deviceName(), r.deviceType(), r.publicKey()));
    }

    @GetMapping("/device/me")
    public ResponseEntity<?> deviceMe(@RequestHeader(value="X-PULSE-DEVICE-TOKEN", required=false) String token) {
        return ResponseEntity.ok(service.authenticateDevice(token));
    }

    @PostMapping("/device/encrypt")
    public ResponseEntity<?> deviceEncrypt(@RequestHeader(value="X-PULSE-DEVICE-TOKEN", required=false) String token, @RequestBody DeviceCryptoRequest r) {
        UniversalKeyService.DeviceView d = service.authenticateDevice(token);
        if (d.keyId() == null || !Arrays.asList(d.scopes()).contains("key:use")) throw new SecurityException("Device is not authorized for key use");
        return ResponseEntity.ok(Map.of("deviceId", d.deviceId(), "ciphertext", keys.encryptSystem(d.organizationId(), d.keyId(), r.plaintext())));
    }

    @PostMapping("/device/decrypt")
    public ResponseEntity<?> deviceDecrypt(@RequestHeader(value="X-PULSE-DEVICE-TOKEN", required=false) String token, @RequestBody DeviceCryptoRequest r) {
        UniversalKeyService.DeviceView d = service.authenticateDevice(token);
        if (d.keyId() == null || !Arrays.asList(d.scopes()).contains("key:use")) throw new SecurityException("Device is not authorized for key use");
        return ResponseEntity.ok(Map.of("deviceId", d.deviceId(), "plaintext", keys.decryptSystem(d.organizationId(), d.keyId(), r.ciphertext())));
    }

    @PostMapping("/devices/{deviceId}/revoke")
    public ResponseEntity<?> revoke(Authentication a, @RequestParam UUID organizationId, @PathVariable UUID deviceId) {
        service.revokeDevice(UUID.fromString(a.getName()), organizationId, deviceId);
        return ResponseEntity.noContent().build();
    }

    public record ActivationRequest(String organizationId,String keyId,Integer ttlMinutes,Integer maxUses,String[] scopes) {}
    public record ActivationPayload(String code,String deviceName,String deviceType,String publicKey) {}
    public record DeviceCryptoRequest(String plaintext,String ciphertext) {}
}
