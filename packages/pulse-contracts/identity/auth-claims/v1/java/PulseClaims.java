package io.pulse.contracts.identity.v1;
import java.util.List;
public record PulseClaims(String sub,long iat,long exp,String organizationId,String orgId,String role,List<String> scopes,String actorType,String sessionId,String iss,String aud){}
