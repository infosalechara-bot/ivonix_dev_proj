package com.ivonix.pulse.ontology.api;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreateEntityRequest(
  @NotNull UUID organizationId,
  @NotNull UUID typeId,
  @NotBlank @Size(max=255) String displayName,
  @Size(max=100000) String properties,
  UUID sourceDeviceId
) {}