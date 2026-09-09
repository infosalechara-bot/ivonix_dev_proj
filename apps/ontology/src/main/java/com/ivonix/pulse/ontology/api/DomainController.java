package com.ivonix.pulse.ontology.api;

import com.ivonix.pulse.ontology.domain.UniversalDomainService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/domains")
public class DomainController {
    private final UniversalDomainService service;
    public DomainController(UniversalDomainService service){this.service=service;}
    @PostMapping("/{domain}/devices/{deviceId}/data")
    public Map<String,Object> ingest(@PathVariable String domain,@PathVariable UUID deviceId,@Valid @RequestBody DomainDataRequest request){service.ingest(domain,deviceId,org(),request.data());return Map.of("accepted",true);}
    @GetMapping("/{domain}/devices/{deviceId}/data")
    public List<Map<String,Object>> latest(@PathVariable String domain,@PathVariable UUID deviceId,@RequestParam(defaultValue="20") @Min(1) @Max(100) int limit){return service.latest(domain,deviceId,org(),limit);}
    @PostMapping("/{domain}/devices/{deviceId}/diagnose")
    public Map<String,Object> diagnose(@PathVariable String domain,@PathVariable UUID deviceId){return service.diagnose(domain,deviceId,org());}
    private UUID org(){return (UUID)SecurityContextHolder.getContext().getAuthentication().getDetails();}
    public record DomainDataRequest(@NotEmpty @Size(max=32) Map<String,Object> data){}
}
