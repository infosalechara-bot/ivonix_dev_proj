package com.ivonix.pulse.ontology.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class JwtService {
  private final SecretKey key;
  public JwtService(@Value("${PULSE_JWT_SECRET}") String secret){
    if(secret.length()<32) throw new IllegalArgumentException("PULSE_JWT_SECRET must be at least 32 characters");
    key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }
  public Claims claims(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();}
  public UUID userId(Claims c){return UUID.fromString(c.getSubject());}
  public UUID organizationId(Claims c){return UUID.fromString(String.valueOf(c.get("organization_id")));}
}