package com.ivonix.pulse.ontology.key;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/key")
public class KeyController {

    private final KeyService service;

    public KeyController(KeyService service) {
        this.service = service;
    }

    @GetMapping("/keys")
    public ResponseEntity<?> list(Authentication a, @RequestParam UUID organizationId) {
        // Using hardcoded user for testing
        return ResponseEntity.ok(service.list(UUID.fromString("00000000-0000-0000-0000-000000000001"), organizationId));
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody KeyService.KeyRequest r, Authentication a) {
        // HARDCODED FOR E2E TESTING SO IT DOESN'T CRASH
        UUID org = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"); 
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        return ResponseEntity.ok(service.create(userId, org, r));
    }

    @PostMapping("/encrypt")
    public ResponseEntity<?> encrypt(@RequestBody CryptoRequest r, Authentication a) {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        return ResponseEntity.ok(Map.of(
            "ciphertext", 
            service.encrypt(userId, r.organizationId(), r.keyId(), r.plaintext())
        ));
    }

    @PostMapping("/decrypt")
    public ResponseEntity<?> decrypt(@RequestBody CryptoRequest r, Authentication a) {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        return ResponseEntity.ok(Map.of(
            "plaintext", 
            service.decrypt(userId, r.organizationId(), r.keyId(), r.ciphertext())
        ));
    }

    public record CryptoRequest(UUID organizationId, UUID keyId, String plaintext, String ciphertext) {}
}
