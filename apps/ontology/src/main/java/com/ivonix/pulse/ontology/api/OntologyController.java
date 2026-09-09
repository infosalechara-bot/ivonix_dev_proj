package com.ivonix.pulse.ontology.api;

import com.ivonix.pulse.ontology.domain.OntologyEntity;
import com.ivonix.pulse.ontology.service.OntologyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/ontology")
public class OntologyController {
  private final OntologyService service;
  public OntologyController(OntologyService service){this.service=service;}
  private UUID org(Authentication auth){return (UUID) auth.getDetails();}
  @PostMapping("/entities") public ResponseEntity<OntologyEntity> create(@Valid @RequestBody CreateEntityRequest request,Authentication auth){return ResponseEntity.ok(service.create(request,org(auth)));}
  @GetMapping("/graph/{id}") public ResponseEntity<List<Map<String,Object>>> graph(@PathVariable UUID id,@RequestParam(defaultValue="2") int depth,Authentication auth){return ResponseEntity.ok(service.graph(id,depth,org(auth)));}
  @GetMapping("/entities/search") public ResponseEntity<List<OntologyEntity>> search(@RequestParam @jakarta.validation.constraints.Size(max=200) String q,Authentication auth){return ResponseEntity.ok(service.search(q,org(auth)));}
}
