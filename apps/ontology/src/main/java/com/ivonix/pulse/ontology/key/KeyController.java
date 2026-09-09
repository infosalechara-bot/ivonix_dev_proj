package com.ivonix.pulse.ontology.key;
import org.springframework.http.ResponseEntity; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/key") public class KeyController {
 private final KeyService service; public KeyController(KeyService service){this.service=service;}
 @GetMapping("/keys") public ResponseEntity<?> list(Authentication a,@RequestParam UUID organizationId){return ResponseEntity.ok(service.list(user(a),organizationId));}
 @PostMapping("/create") public ResponseEntity<?> create(@RequestBody KeyService.KeyRequest r,Authentication a){UUID org=UUID.fromString(r.organizationId());return ResponseEntity.ok(service.create(user(a),org,r));}
 @PostMapping("/encrypt") public ResponseEntity<?> encrypt(@RequestBody CryptoRequest r,Authentication a){return ResponseEntity.ok(Map.of("ciphertext",service.encrypt(user(a),r.organizationId(),r.keyId(),r.plaintext())));}
 @PostMapping("/decrypt") public ResponseEntity<?> decrypt(@RequestBody CryptoRequest r,Authentication a){return ResponseEntity.ok(Map.of("plaintext",service.decrypt(user(a),r.organizationId(),r.keyId(),r.ciphertext())));}
 private UUID user(Authentication a){return UUID.fromString(a.getName());}
 public record CryptoRequest(UUID organizationId,UUID keyId,String plaintext,String ciphertext){}
}