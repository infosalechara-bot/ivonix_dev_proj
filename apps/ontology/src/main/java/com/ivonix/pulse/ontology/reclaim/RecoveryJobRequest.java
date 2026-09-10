package com.ivonix.pulse.ontology.reclaim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RecoveryJobRequest(
        @NotNull UUID organizationId,
        UUID deviceId,
        @NotBlank @Pattern(regexp = "file|message|database|system") String jobType,
        @Size(max = 1000) String targetPath,
        @Size(max = 256) String targetTable,
        @Size(max = 512) String targetIdentifier,
        @NotNull UUID founderConfirmationId) {}
