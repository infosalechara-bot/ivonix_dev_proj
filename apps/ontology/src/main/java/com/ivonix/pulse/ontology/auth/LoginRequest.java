package com.ivonix.pulse.ontology.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LoginRequest(
    @Email @NotBlank String email,
    @NotBlank String password,
    @NotNull UUID organizationId) {}
