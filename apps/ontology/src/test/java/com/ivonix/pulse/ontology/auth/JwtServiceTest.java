package com.ivonix.pulse.ontology.auth;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
  @Test void issuesAndParsesOrganizationScopedToken(){
    JwtService service=new JwtService("01234567890123456789012345678901",900);
    UUID user=UUID.randomUUID(),org=UUID.randomUUID();
    String token=service.issue(user,org);
    assertEquals(user,service.userId(service.claims(token)));
    assertEquals(org,service.organizationId(service.claims(token)));
    assertThrows(Exception.class,()->service.claims(token+"x"));
  }
  @Test void rejectsWeakSecret(){assertThrows(IllegalArgumentException.class,()->new JwtService("short",900));}
}
