package com.ivonix.pulse.ontology.api;

import com.ivonix.pulse.ontology.domain.OntologyEntity;
import com.ivonix.pulse.ontology.service.OntologyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/ontology")
public class OntologyController {
  private final OntologyService service;
  public OntologyController(OntologyService service){this.service=service;}

  @PostMapping("/entities")
  public ResponseEntity<OntologyEntity> create(@Valid @RequestBody CreateEntityRequest request,
                                                @RequestHeader("X-Organization-Id") UUID orgId){
    return ResponseEntity.ok(service.create(request,orgId));
  }

  @GetMapping("/graph/{id}")
  public ResponseEntity<List<Map<String,Object>>> graph(@PathVariable UUID id,
      @RequestParam(defaultValue="2") int depth,
      @RequestHeader("X-Organization-Id") UUID orgId){
    return ResponseEntity.ok(service.graph(id,depth,orgId));
  }

  @GetMapping("/entities/search")
  public ResponseEntity<List<OntologyEntity>> search(@RequestParam String q,
      @RequestHeader("X-Organization-Id") UUID orgId){
    return ResponseEntity.ok(service.search(q,orgId));
  }
}