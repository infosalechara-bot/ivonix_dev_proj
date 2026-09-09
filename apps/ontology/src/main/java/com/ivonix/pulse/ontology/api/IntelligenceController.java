package com.ivonix.pulse.ontology.api;

import com.ivonix.pulse.ontology.intelligence.InvestigativeService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/intelligence")
public class IntelligenceController {
    private final InvestigativeService service;
    public IntelligenceController(InvestigativeService service){this.service=service;}

    private UUID user(){ return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName()); }
    private UUID org(){
        Object details=SecurityContextHolder.getContext().getAuthentication().getDetails();
        if(!(details instanceof UUID)) throw new SecurityException("Organization context missing");
        return (UUID)details;
    }

    @PostMapping("/cases")
    public Map<String,Object> createCase(@RequestBody InvestigativeService.CaseRequest request){
        return Map.of("id",service.createCase(org(),user(),request));
    }
    @GetMapping("/cases") public Object cases(){return service.listCases(org(),user());}

    @PostMapping("/cases/{caseId}/evidence")
    public Map<String,Object> evidence(@PathVariable UUID caseId,@RequestBody InvestigativeService.EvidenceRequest request){
        service.addEvidence(org(),user(),caseId,request); return Map.of("status","created");
    }
    @GetMapping("/cases/{caseId}/evidence") public Object evidence(@PathVariable UUID caseId){return service.getEvidence(org(),user(),caseId);}

    @PostMapping("/cases/{caseId}/timeline")
    public Map<String,Object> timeline(@PathVariable UUID caseId,@RequestBody InvestigativeService.TimelineRequest request){
        service.addTimeline(org(),user(),caseId,request); return Map.of("status","created");
    }
    @GetMapping("/cases/{caseId}/timeline") public Object timeline(@PathVariable UUID caseId){return service.getTimeline(org(),user(),caseId);}

    @PostMapping("/cases/{caseId}/links")
    public Map<String,Object> link(@PathVariable UUID caseId,@RequestBody InvestigativeService.LinkRequest request){
        service.addLink(org(),user(),caseId,request); return Map.of("status","created");
    }
    @GetMapping("/cases/{caseId}/links") public Object links(@PathVariable UUID caseId){return service.getLinks(org(),user(),caseId);}

    @GetMapping("/link-analysis/common-neighbors")
    public Object commonNeighbors(@RequestParam UUID entityA,@RequestParam UUID entityB){return service.commonNeighbors(org(),user(),entityA,entityB);}
    @GetMapping("/link-analysis/common-neighbor-count")
    public Map<String,Object> commonNeighborCount(@RequestParam UUID entityA,@RequestParam UUID entityB){return Map.of("count",service.commonNeighborCount(org(),user(),entityA,entityB));}
    @GetMapping("/link-analysis/shortest-path")
    public Object shortestPath(@RequestParam UUID start,@RequestParam UUID end){return service.shortestPath(org(),user(),start,end);}
}
