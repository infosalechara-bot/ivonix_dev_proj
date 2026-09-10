package io.pulse.contracts.identity.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PulseClaims(
    String sub,
    long iat,
    long exp,
    @JsonProperty("organization_id") String organizationId,
    @JsonProperty("org_id") String orgId,
    String role,
    List<String> scopes,
    @JsonProperty("actor_type") String actorType,
    @JsonProperty("session_id") String sessionId,
    String iss,
    String aud
) {}
