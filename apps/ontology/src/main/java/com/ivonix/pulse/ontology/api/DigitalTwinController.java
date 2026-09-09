package com.ivonix.pulse.ontology.api;

import com.ivonix.pulse.ontology.digitaltwin.DigitalTwinService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/digital-twin")
public class DigitalTwinController {
    private final DigitalTwinService service;
    public DigitalTwinController(DigitalTwinService service) { this.service = service; }
    private UUID user() { return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName()); }
    private UUID org() {
        Object d = SecurityContextHolder.getContext().getAuthentication().getDetails();
        if (!(d instanceof UUID)) throw new SecurityException("Organization context missing");
        return (UUID)d;
    }

    @PostMapping("/twins")
    public Map<String,Object> create(@Valid @RequestBody TwinRequest r) {
        return Map.of("id", service.createTwin(org(), user(), new DigitalTwinService.TwinRequest(r.deviceId(), r.name(), r.simulationModel(), r.parameters())));
    }
    @GetMapping("/twins")
    public Object twins(@RequestParam UUID deviceId) { return service.twins(org(), user(), deviceId); }
    @PostMapping("/twins/{twinId}/simulate")
    public Map<String,Object> simulate(@PathVariable UUID twinId, @RequestBody(required=false) Map<String,Object> inputData) {
        return Map.of("runId", service.runSimulation(org(), user(), twinId, inputData));
    }
    @GetMapping("/devices/{deviceId}/predictions")
    public Object predictions(@PathVariable UUID deviceId) { return service.predictions(org(), user(), deviceId); }
    @GetMapping("/twins/{twinId}/snapshots")
    public Object snapshots(@PathVariable UUID twinId) { return service.snapshots(org(), user(), twinId); }

    public record TwinRequest(@NotNull UUID deviceId, @NotBlank String name, @NotBlank String simulationModel, Map<String,Object> parameters) {}
}
