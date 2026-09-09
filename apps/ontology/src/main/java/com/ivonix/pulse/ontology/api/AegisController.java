package com.ivonix.pulse.ontology.api;

import com.ivonix.pulse.ontology.aegis.SpaceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/aegis")
public class AegisController {
    private final SpaceService service;
    public AegisController(SpaceService service) { this.service = service; }
    private UUID user(){ return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName()); }
    private UUID org(){ Object d=SecurityContextHolder.getContext().getAuthentication().getDetails(); if(!(d instanceof UUID)) throw new SecurityException("Organization context missing"); return (UUID)d; }

    @PostMapping("/satellites")
    public Map<String,Object> register(@Valid @RequestBody SatelliteRequest r){ return Map.of("id",service.ingestSatellite(org(),user(),new SpaceService.SatelliteRequest(r.name(),r.noradId(),r.tleLine1(),r.tleLine2(),r.status()))); }
    @GetMapping("/satellites")
    public Object satellites(){ return service.satellites(org(),user()); }
    @GetMapping("/satellites/{satelliteId}/collisions")
    public Object collisions(@PathVariable UUID satelliteId){ return service.predictions(org(),user(),satelliteId); }
    @GetMapping("/satellites/{satelliteId}/comms")
    public Object comms(@PathVariable UUID satelliteId){ return service.comms(org(),user(),satelliteId); }

    public record SatelliteRequest(@NotBlank String noradId,String name,String tleLine1,String tleLine2,String status) {}
}
