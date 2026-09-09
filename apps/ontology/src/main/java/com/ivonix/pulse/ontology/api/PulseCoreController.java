package com.ivonix.pulse.ontology.api;

import com.ivonix.pulse.ontology.find.FindService;
import com.ivonix.pulse.ontology.intelligence.OntologyIntelligenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.Instant; import java.util.*;

@RestController @RequestMapping("/api/v1")
public class PulseCoreController {
 private final FindService find; private final OntologyIntelligenceService ontology;
 public PulseCoreController(FindService find,OntologyIntelligenceService ontology){this.find=find;this.ontology=ontology;}
 @PostMapping("/find/registrations") public ResponseEntity<UUID> register(@Valid @RequestBody RegistrationRequest r){return ResponseEntity.ok(find.register(r.serviceId(),org(),user(),r.entityType(),r.entityData(),r.consentAt(),r.consentVersion()));}
 @PostMapping("/find/searches") public ResponseEntity<UUID> search(@Valid @RequestBody SearchRequest r){return ResponseEntity.ok(find.createSearch(r.serviceId(),org(),user(),r.query()));}
 @PostMapping("/find/searches/{searchId}/matches") public List<Map<String,Object>> matches(@PathVariable UUID searchId,@Valid @RequestBody VectorSearchRequest r){return find.search(searchId,org(),user(),r.embedding(),r.embeddingType(),r.limit());}
 @GetMapping("/ontology/entities/{entityId}/graph") public List<Map<String,Object>> graph(@PathVariable UUID entityId,@RequestParam(defaultValue="2") int depth){return ontology.graph(entityId,depth,org());}
 @GetMapping("/ontology/pagerank") public List<Map<String,Object>> pageRank(@RequestParam(defaultValue="20") int iterations){return ontology.pageRank(org(),iterations);}
 @PostMapping("/ontology/relationships") public ResponseEntity<Void> relationship(@Valid @RequestBody RelationshipRequest r){ontology.relationship(r.sourceId(),r.targetId(),r.type(),org());return ResponseEntity.noContent().build();}
 private UUID user(){return (UUID)SecurityContextHolder.getContext().getAuthentication().getPrincipal();} private UUID org(){return (UUID)SecurityContextHolder.getContext().getAuthentication().getDetails();}
 public record RegistrationRequest(@NotNull UUID serviceId,@NotBlank @Size(max=32) String entityType,@NotNull Map<String,Object> entityData,@NotNull Instant consentAt,@NotBlank @Size(max=64) String consentVersion){}
 public record SearchRequest(@NotNull UUID serviceId,@NotNull Map<String,Object> query){}
 public record VectorSearchRequest(@NotBlank String embedding,@NotBlank @Pattern(regexp="face|voice|image|text") String embeddingType,@Min(1) @Max(50) int limit){}
 public record RelationshipRequest(@NotNull UUID sourceId,@NotNull UUID targetId,@NotBlank @Size(max=64) String type){}
}
