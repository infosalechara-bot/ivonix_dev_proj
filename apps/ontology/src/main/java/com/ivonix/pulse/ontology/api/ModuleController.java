package com.ivonix.pulse.ontology.api;

import com.ivonix.pulse.ontology.engineering.EngineeringService;
import com.ivonix.pulse.ontology.shield.ShieldService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class ModuleController {
    private final EngineeringService engineering; private final ShieldService shield;
    public ModuleController(EngineeringService engineering,ShieldService shield){this.engineering=engineering;this.shield=shield;}
    @PostMapping("/engineering/diagnose")
    public Map<String,Object> diagnose(@Valid @RequestBody DiagnosisRequest r){return engineering.diagnose(r.deviceId(),org(),user(),r.evidenceIds(),r.telemetry());}
    @PostMapping("/shield/events")
    public Map<String,Object> event(@Valid @RequestBody SecurityEventRequest r){long id=shield.ingest(org(),user(),r.deviceId(),r.eventType(),r.severity(),r.details(),r.sourceIp());return Map.of("id",id);}
    @PostMapping("/shield/scan")
    public Map<String,Object> scan(@Valid @RequestBody HashRequest r){return shield.scanHash(org(),user(),r.hash());}
    private UUID user(){return (UUID)SecurityContextHolder.getContext().getAuthentication().getPrincipal();}
    private UUID org(){return (UUID)SecurityContextHolder.getContext().getAuthentication().getDetails();}
    public record DiagnosisRequest(@NotNull UUID deviceId,@Size(max=100) List<UUID> evidenceIds,@NotNull Map<String,Object> telemetry){}
    public record SecurityEventRequest(UUID deviceId,@NotBlank String eventType,@NotBlank String severity,Map<String,Object> details,@Pattern(regexp="^$|^[0-9a-fA-F:.]+$") String sourceIp){}
    public record HashRequest(@NotBlank @Pattern(regexp="^[A-Fa-f0-9]{32,128}$") String hash){}
}
