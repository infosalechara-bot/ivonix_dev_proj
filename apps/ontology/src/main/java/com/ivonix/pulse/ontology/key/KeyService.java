package com.ivonix.pulse.ontology.key;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Service
public class KeyService {
  private final JdbcTemplate jdbc;
  private final RestTemplate rest;
  private final String cryptoUrl;
  private final SecureRandom random = new SecureRandom();

  public KeyService(JdbcTemplate jdbc, RestTemplate rest, @Value("${PULSE_CRYPTO_URL:http://127.0.0.1:8092}") String cryptoUrl) {
    this.jdbc = jdbc; this.rest = rest; this.cryptoUrl = cryptoUrl.replaceAll("/$", "");
  }

  public KeyView create(UUID userId, UUID orgId, KeyRequest r) {
    requireMember(orgId, userId);
    String type = required(r.keyType(), "keyType");
    String purpose = required(r.purpose(), "purpose");
    String alias = required(r.keyAlias(), "keyAlias");
    if (!Set.of("aes-256","rsa-2048","ec-p256").contains(type)) throw new IllegalArgumentException("Unsupported key type");
    if (!Set.of("encryption","signing","key_exchange").contains(purpose)) throw new IllegalArgumentException("Unsupported purpose");
    KeyMaterial m = generate(type);
    UUID id = UUID.randomUUID();
    jdbc.update("insert into public.crypto_keys(id,organization_id,key_alias,key_type,purpose,hsm_backed,public_key,encrypted_private_key,created_by) values (?,?,?,?,?,?,?,?,?)",
        id, orgId, alias, type, purpose, false, m.publicKey(), m.encryptedPrivateKey(), userId);
    audit(id, "rotate", userId, "success");
    return get(id, userId, orgId);
  }

  public String encrypt(UUID userId, UUID orgId, UUID keyId, String plaintext) {
    KeyView k = authorizeKey(keyId, userId, orgId, "encryption");
    CryptoResponse r = rest.postForObject(cryptoUrl + "/v1/encrypt", new CryptoRequest(k.id(), plaintext), CryptoResponse.class);
    audit(keyId, "encrypt", userId, "success");
    return r.ciphertext();
  }

  public String decrypt(UUID userId, UUID orgId, UUID keyId, String ciphertext) {
    KeyView k = authorizeKey(keyId, userId, orgId, "encryption");
    CryptoResponse r = rest.postForObject(cryptoUrl + "/v1/decrypt", new CryptoRequest(k.id(), ciphertext), CryptoResponse.class);
    audit(keyId, "decrypt", userId, "success");
    return r.plaintext();
  }

  public KeyView get(UUID keyId, UUID userId, UUID orgId) {
    authorizeKey(keyId,userId,orgId,null);
    return jdbc.queryForObject("select id,key_alias,key_type,purpose,hsm_backed,public_key,created_at,last_rotated_at,rotation_interval_days,status from public.crypto_keys where id=? and organization_id=?",
        (rs,n)->new KeyView((UUID)rs.getObject("id"),rs.getString("key_alias"),rs.getString("key_type"),rs.getString("purpose"),rs.getBoolean("hsm_backed"),rs.getString("public_key"),rs.getObject("created_at",java.time.Instant.class),rs.getObject("last_rotated_at",java.time.Instant.class),rs.getInt("rotation_interval_days"),rs.getString("status")),keyId,orgId);
  }

  public java.util.List<KeyView> list(UUID userId, UUID orgId) {
    requireMember(orgId,userId);
    return jdbc.query("select id,key_alias,key_type,purpose,hsm_backed,public_key,created_at,last_rotated_at,rotation_interval_days,status from public.crypto_keys where organization_id=? order by created_at desc",
      (rs,n)->new KeyView((UUID)rs.getObject("id"),rs.getString("key_alias"),rs.getString("key_type"),rs.getString("purpose"),rs.getBoolean("hsm_backed"),rs.getString("public_key"),rs.getObject("created_at",java.time.Instant.class),rs.getObject("last_rotated_at",java.time.Instant.class),rs.getInt("rotation_interval_days"),rs.getString("status")),orgId);
  }

  private KeyView authorizeKey(UUID id, UUID userId, UUID orgId, String purpose) {
    requireMember(orgId,userId);
    KeyView k=getUnchecked(id,orgId);
    if (k==null || !"active".equals(k.status())) throw new SecurityException("Key unavailable");
    if (purpose!=null && !purpose.equals(k.purpose())) throw new SecurityException("Key purpose mismatch");
    return k;
  }
  private KeyView getUnchecked(UUID id, UUID orgId) { try { return jdbc.queryForObject("select id,key_alias,key_type,purpose,hsm_backed,public_key,created_at,last_rotated_at,rotation_interval_days,status from public.crypto_keys where id=? and organization_id=?",(rs,n)->new KeyView((UUID)rs.getObject("id"),rs.getString("key_alias"),rs.getString("key_type"),rs.getString("purpose"),rs.getBoolean("hsm_backed"),rs.getString("public_key"),rs.getObject("created_at",java.time.Instant.class),rs.getObject("last_rotated_at",java.time.Instant.class),rs.getInt("rotation_interval_days"),rs.getString("status")),id,orgId); } catch(Exception e){ return null; } }
  private void requireMember(UUID orgId, UUID userId){ Integer n=jdbc.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?",Integer.class,orgId,userId); if(n==null||n<1) throw new SecurityException("Organization membership required"); }
  private void audit(UUID id,String op,UUID userId,String status){ jdbc.update("insert into public.crypto_operations(key_id,operation_type,requested_by,status) values (?,?,?,?)",id,op,userId,status); }
  private String required(String s,String n){if(s==null||s.isBlank())throw new IllegalArgumentException(n+" required");return s.trim();}
  private KeyMaterial generate(String type){ try { if("aes-256".equals(type)){byte[] b=new byte[32];random.nextBytes(b);return new KeyMaterial(null, Base64.getEncoder().encodeToString(b));} KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);KeyPair p=g.generateKeyPair();return new KeyMaterial(Base64.getEncoder().encodeToString(p.getPublic().getEncoded()),Base64.getEncoder().encodeToString(p.getPrivate().getEncoded())); } catch(Exception e){throw new IllegalStateException("Key generation failed",e);} }
  public record KeyRequest(String organizationId,String keyAlias,String keyType,String purpose) {}
  public record KeyView(UUID id,String keyAlias,String keyType,String purpose,boolean hsmBacked,String publicKey,java.time.Instant createdAt,java.time.Instant lastRotatedAt,int rotationIntervalDays,String status) {}
  public record CryptoRequest(UUID keyId,String data) {}
  public record CryptoResponse(String ciphertext,String plaintext) {}
  private record KeyMaterial(String publicKey,String encryptedPrivateKey) {}
}