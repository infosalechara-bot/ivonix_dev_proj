package com.ivonix.pulse.ontology.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="refresh_tokens", schema="public")
public class RefreshToken {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
  @Column(name="user_id", nullable=false) private UUID userId;
  @Column(name="organization_id", nullable=false) private UUID organizationId;
  @Column(name="token_hash", nullable=false, unique=true, length=64) private String tokenHash;
  @Column(name="expires_at", nullable=false) private Instant expiresAt;
  @Column(name="created_at", nullable=false) private Instant createdAt=Instant.now();
  @Column(name="revoked_at") private Instant revokedAt;
  public UUID getId(){return id;} public UUID getUserId(){return userId;} public void setUserId(UUID v){userId=v;}
  public UUID getOrganizationId(){return organizationId;} public void setOrganizationId(UUID v){organizationId=v;}
  public String getTokenHash(){return tokenHash;} public void setTokenHash(String v){tokenHash=v;}
  public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;}
  public Instant getCreatedAt(){return createdAt;} public Instant getRevokedAt(){return revokedAt;} public void setRevokedAt(Instant v){revokedAt=v;}
}
