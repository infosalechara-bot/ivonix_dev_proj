package com.ivonix.pulse.ontology.api;

import com.ivonix.pulse.ontology.find.FindService;
import com.ivonix.pulse.ontology.intelligence.OntologyIntelligenceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PulseCoreController {
    private final FindService find; private final OntologyIntelligenceService ontology;
    public PulseCoreController(FindService find, OntologyIntelligenceService ontology){this.find=find;this.ontology=ontology;}

    @PostMapping("/find/registrations")
    public ResponseEntity<UUID> register(@Valid @RequestBody RegistrationRequest r){ return ResponseEntity.ok(find.register(r.serviceId(), org(), user(), r.entityType(), r.entityData())); }

    @PostMapping("/find/searches")
    public ResponseEntity<UUID> search(@Valid @RequestBody SearchRequest r){
        UUID id=find.createSearch(r.serviceId(),org(),user(),r.query());
        String embedding=find.generateEmbedding(r.embeddingType(),r.source(),r.text());
        return ResponseEntity.ok(id);
    }

    @GetMapping("/ontology/entities/{entityId}/graph")
    public List<Map<String,Object>> graph(@PathVariable UUID entityId,@RequestParam(defaultValue="2") int depth){return ontology.graph(entityId,depth,org());}

    @GetMapping("/ontology/pagerank")
    public List<Map<String,Object>> pageRank(@RequestParam(defaultValue="20") int iterations){return ontology.pageRank(org(),iterations);}

    @PostMapping("/ontology/relationships")
    public ResponseEntity<Void> relationship(@Valid @RequestBody RelationshipRequest r){ontology.relationship(r.sourceId(),r.targetId(),r.type(),org());return ResponseEntity.noContent().build();}

    private UUID user(){return (UUID)SecurityContextHolder.getContext().getAuthentication().getPrincipal();}
    private UUID org(){return (UUID)SecurityContextHolder.getContext().getAuthentication().getDetails();}

    public record RegistrationRequest(@NotNull UUID serviceId,@NotBlank @Size(max=32) String entityType,@NotNull Map<String,Object> entityData){}
    public record SearchRequest(@NotNull UUID serviceId,@NotBlank @Size(max=16) String embeddingType,@Size(max=4096) String text,@Size(max=2048) String source,@NotNull Map<String,Object> query){}
    public record RelationshipRequest(@NotNull UUID sourceId,@NotNull UUID targetId,@NotBlank @Size(max=64) String type){}
}
