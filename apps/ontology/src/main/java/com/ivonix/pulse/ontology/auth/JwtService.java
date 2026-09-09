package com.ivonix.pulse.ontology.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
  private final SecretKey key; private final long accessSeconds;
  public JwtService(@Value("${PULSE_JWT_SECRET}") String secret,@Value("${PULSE_ACCESS_TOKEN_SECONDS:900}") long accessSeconds){
    if(secret==null || secret.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalArgumentException("PULSE_JWT_SECRET must be at least 32 bytes");
    if(accessSeconds<60 || accessSeconds>3600) throw new IllegalArgumentException("PULSE_ACCESS_TOKEN_SECONDS must be 60..3600");
    key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); this.accessSeconds=accessSeconds;
  }
  public String issue(UUID userId,UUID organizationId){
    Instant now=Instant.now(); return Jwts.builder().subject(userId.toString()).claim("organization_id",organizationId.toString()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(accessSeconds))).signWith(key).compact();
  }
  public Claims claims(String token){return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();}
  public UUID userId(Claims c){return UUID.fromString(c.getSubject());}
  public UUID organizationId(Claims c){return UUID.fromString(String.valueOf(c.get("organization_id")));}
  public long accessSeconds(){return accessSeconds;}
}
