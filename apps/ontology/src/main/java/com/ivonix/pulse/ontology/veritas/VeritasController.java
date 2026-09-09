package com.ivonix.pulse.ontology.veritas;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/veritas")
public class VeritasController {
  private final VeritasService service;
  public VeritasController(VeritasService service){this.service=service;}
  private UUID user(Authentication a){ return UUID.fromString(a.getName()); }
  @PostMapping("/sessions") public ResponseEntity<UUID> create(@RequestBody VeritasService.SessionRequest r, Authentication a){return ResponseEntity.ok(service.createSession(r,user(a)));}
  @PostMapping("/sessions/{id}/evidence") public ResponseEntity<UUID> evidence(@PathVariable UUID id,@RequestBody VeritasService.EvidenceRequest r,Authentication a){return ResponseEntity.ok(service.addEvidence(id,r,user(a)));}
  @GetMapping("/sessions/{id}/results") public ResponseEntity<?> results(@PathVariable UUID id,Authentication a){return ResponseEntity.ok(service.results(id,user(a)));}
  @GetMapping("/analysis/{id}/markers") public ResponseEntity<?> markers(@PathVariable UUID id,Authentication a){return ResponseEntity.ok(service.markers(id,user(a)));}
}
