package com.ivonix.pulse.ontology.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
  private final RestClient supabase;
  private final String supabaseKey;
  private final RefreshTokenRepository tokens;
  private final JwtService jwt;
  private final AuditService audit;
  private final long refreshSeconds;
  private final SecureRandom random = new SecureRandom();

  public AuthService(@Value("${SUPABASE_URL}") String supabaseUrl,
                     @Value("${SUPABASE_PUBLISHABLE_KEY}") String supabaseKey,
                     RefreshTokenRepository tokens, JwtService jwt, AuditService audit,
                     @Value("${PULSE_REFRESH_TOKEN_SECONDS:2592000}") long refreshSeconds) {
    this.supabaseKey=supabaseKey;
    if(refreshSeconds < 86400 || refreshSeconds > 7776000) throw new IllegalArgumentException("PULSE_REFRESH_TOKEN_SECONDS must be 1..90 days");
    this.refreshSeconds=refreshSeconds;
    this.supabase=RestClient.builder().baseUrl(supabaseUrl).build();
  }

  @Transactional
  public AuthResponse login(LoginRequest request, HttpServletRequest http) {
    Map<?,?> result = supabase.post().uri("/auth/v1/token?grant_type=password")
      .contentType(MediaType.APPLICATION_JSON)
      .header("apikey",supabaseKey).body(Map.of("email",request.email(),"password",request.password()))
      .retrieve().body(Map.class);
    if(result==null || result.get("access_token")==null || !(result.get("user") instanceof Map<?,?> user))
      throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
    UUID userId=UUID.fromString(String.valueOf(user.get("id")));
    ensureMembership(userId,request.organizationId());
    String access=jwt.issue(userId,request.organizationId());
    String refresh=randomToken(); store(userId,request.organizationId(),refresh);
    audit.record(userId,"login","user",userId.toString(),http);
    return new AuthResponse(access,refresh,jwt.accessSeconds());
  }

  @Transactional
  public AuthResponse refresh(String raw,HttpServletRequest http) {
    String hash=hash(raw);
    RefreshToken old=tokens.findForUpdate(hash).orElseThrow(()->new org.springframework.security.authentication.BadCredentialsException("Invalid refresh token"));
    if(old.getRevokedAt()!=null || !old.getExpiresAt().isAfter(Instant.now())) throw new org.springframework.security.authentication.BadCredentialsException("Refresh token expired or revoked");
    ensureMembership(old.getUserId(),old.getOrganizationId());
    old.setRevokedAt(Instant.now()); tokens.save(old);
    String next=randomToken(); store(old.getUserId(),old.getOrganizationId(),next);
    audit.record(old.getUserId(),"refresh_token","user",old.getUserId().toString(),http);
    return new AuthResponse(jwt.issue(old.getUserId(),old.getOrganizationId()),next,jwt.accessSeconds());
  }

  @Transactional
  public void revoke(String raw,HttpServletRequest http) {
    tokens.findForUpdate(hash(raw)).ifPresent(t->{ if(t.getRevokedAt()==null){t.setRevokedAt(Instant.now());tokens.save(t);audit.record(t.getUserId(),"logout","session",t.getId().toString(),http);} });
  }

  private void store(UUID user,UUID org,String raw){
    RefreshToken t=new RefreshToken(); t.setUserId(user); t.setOrganizationId(org); t.setTokenHash(hash(raw)); t.setExpiresAt(Instant.now().plusSeconds(refreshSeconds)); tokens.save(t);
  }
  private void ensureMembership(UUID user,UUID org){
    // The membership check is performed against the trusted database, never against a client header.
    Integer count = new org.springframework.jdbc.core.JdbcTemplate(((org.springframework.jdbc.datasource.DataSourceUtils.class)==null)?null:null).queryForObject("select count(*) from public.organization_members where user_id=? and organization_id=?",Integer.class,user,org);
    if(count==null || count==0) throw new org.springframework.security.access.AccessDeniedException("User is not a member of this organization");
  }
  private String randomToken(){byte[] b=new byte[48];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
  private String hash(String raw){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
