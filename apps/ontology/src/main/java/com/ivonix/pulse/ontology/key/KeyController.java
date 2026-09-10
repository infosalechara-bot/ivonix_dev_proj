package com.ivonix.pulse.ontology.key;
import org.springframework.http.ResponseEntity; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/key") public class KeyController {
 private final KeyService service; public KeyController(KeyService service){this.service=service;}
 @GetMapping("/keys") public ResponseEntity<?> list(Authentication a,@RequestParam UUID organizationId){return ResponseEntity.ok(service.list(user(a),organizationId));}
 @PostMapping("/create") public ResponseEntity<?> create(@RequestBody KeyService.KeyRequest r,Authentication a){UUID org=UUID.fromString(r.organizationId());return ResponseEntity.ok(service.create(user(a),org,r));}
 @PostMapping("/encrypt") public ResponseEntity<?> encrypt(@RequestBody CryptoRequest r,Authentication a,@RequestHeader(value="Idempotency-Key",required=false) String operationId){return ResponseEntity.ok(Map.of("ciphertext",service.encrypt(user(a),r.organizationId(),r.keyId(),r.plaintext(),parseOperationId(operationId))));}
 @PostMapping("/decrypt") public ResponseEntity<?> decrypt(@RequestBody CryptoRequest r,Authentication a,@RequestHeader(value="Idempotency-Key",required=false) String operationId){return ResponseEntity.ok(Map.of("plaintext",service.decrypt(user(a),r.organizationId(),r.keyId(),r.ciphertext(),parseOperationId(operationId))));}
 private UUID user(Authentication a){return UUID.fromString(a.getName());}
 private UUID parseOperationId(String value){if(value==null||value.isBlank())return null;try{return UUID.fromString(value.trim());}catch(IllegalArgumentException e){throw new IllegalArgumentException("Idempotency-Key must be a UUID");}}
 public record CryptoRequest(UUID organizationId,UUID keyId,String plaintext,String ciphertext){}
}