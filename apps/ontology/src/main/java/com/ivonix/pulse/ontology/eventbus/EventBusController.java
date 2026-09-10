package com.ivonix.pulse.ontology.eventbus;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/event-bus")
public class EventBusController {
    private final EventBusService service;
    public EventBusController(EventBusService service) { this.service = service; }

    @PostMapping("/publish")
    public ResponseEntity<?> publish(@RequestBody PublishRequest r, Authentication a) {
        UUID user = UUID.fromString(a.getName());
        return ResponseEntity.ok(Map.of("eventId", service.publish(r.eventType(), r.sourceService(), r.payload(), r.organizationId(), r.correlationId(), user)));
    }

    @GetMapping("/events")
    public ResponseEntity<?> events(@RequestParam UUID organizationId, @RequestParam(defaultValue = "50") int limit, Authentication a) {
        return ResponseEntity.ok(service.recent(organizationId, UUID.fromString(a.getName()), limit));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<?> subscribe(@RequestBody EventBusService.SubscriptionRequest r, Authentication a) {
        return ResponseEntity.ok(service.subscribe(UUID.fromString(a.getName()), r));
    }

    public record PublishRequest(UUID organizationId, String eventType, String sourceService, Map<String,Object> payload, String correlationId) {}
}
