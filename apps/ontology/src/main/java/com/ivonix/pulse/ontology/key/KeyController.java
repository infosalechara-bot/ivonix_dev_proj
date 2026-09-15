package com.ivonix.pulse.ontology.key;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/key")
public class KeyController {

    private final KeyService service;

    public KeyController(KeyService service) {
        this.service = service;
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody KeyService.KeyRequest r, Authentication a) {
        UUID org = UUID.fromString(r.organizationId());
        // TODO: restore proper user lookup once Authentication is wired
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        return ResponseEntity.ok(service.create(userId, org, r));
    }
}
