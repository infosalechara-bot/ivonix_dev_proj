package com.ivonix.pulse.ontology.reclaim;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reclaim")
public class RecoveryController {
    private final RecoveryService recovery;

    public RecoveryController(RecoveryService recovery) { this.recovery = recovery; }

    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@Valid @RequestBody RecoveryJobRequest request, Authentication auth) {
        return ResponseEntity.ok(recovery.createRecoveryJob(request, userId(auth)));
    }

    @GetMapping("/organizations/{organizationId}/jobs")
    public ResponseEntity<?> jobs(@PathVariable UUID organizationId, Authentication auth) {
        return ResponseEntity.ok(recovery.getJobs(organizationId, userId(auth)));
    }

    @GetMapping("/jobs/{jobId}/items")
    public ResponseEntity<?> items(@PathVariable UUID jobId, Authentication auth) {
        return ResponseEntity.ok(recovery.getRecoveredItems(jobId, userId(auth)));
    }

    @GetMapping("/jobs/{jobId}/logs")
    public ResponseEntity<?> logs(@PathVariable UUID jobId, Authentication auth) {
        return ResponseEntity.ok(recovery.getLogs(jobId, userId(auth)));
    }

    private UUID userId(Authentication auth) {
        if (auth == null || auth.getName() == null) throw new SecurityException("Authentication required");
        return UUID.fromString(auth.getName());
    }
}
