package com.ivonix.pulse.ontology.key;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class UniversalKeyService {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public UniversalKeyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ActivationCode createActivationCode(UUID userId, UUID orgId, UUID keyId, int ttlMinutes, int maxUses, String[] scopes) {
        requireMember(orgId, userId);
        if (ttlMinutes < 1 || ttlMinutes > 10080) throw new IllegalArgumentException("ttlMinutes must be 1..10080");
        if (maxUses < 1 || maxUses > 1000) throw new IllegalArgumentException("maxUses must be 1..1000");
        if (keyId != null) jdbc.queryForObject("select id from public.crypto_keys where id=? and organization_id=?", UUID.class, keyId, orgId);
        String code = randomCode();
        String hash = sha256(code);
        String prefix = code.substring(0, 4);
        Instant expires = Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES);
        jdbc.update("insert into public.key_activation_codes(organization_id,key_id,code_hash,code_prefix,scope,max_uses,expires_at,created_by) values (?,?,?,?,?,?,?,?)",
                orgId, keyId, hash, prefix, normalizeScopes(scopes), maxUses, expires, userId);
        return new ActivationCode(code, prefix, expires, maxUses, orgType(orgId), scopes == null ? List.of("key:use") : List.of(scopes));
    }

    public ActivationResult activate(String code, String deviceName, String deviceType, String publicKey) {
        if (code == null || code.isBlank()) throw new SecurityException("Activation code required");
        if (deviceName == null || deviceName.isBlank()) throw new IllegalArgumentException("deviceName required");
        String hash = sha256(code.trim().toUpperCase(Locale.ROOT));
        Map<String,Object> row = jdbc.queryForMap("select id,organization_id,key_id,scope,max_uses,used_count,expires_at,revoked_at from public.key_activation_codes where code_hash=? for update", hash);
        UUID activationId = (UUID) row.get("id");
        Instant expires = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
        int used = ((Number) row.get("used_count")).intValue();
        int max = ((Number) row.get("max_uses")).intValue();
        if (row.get("revoked_at") != null || expires.isBefore(Instant.now()) || used >= max) throw new SecurityException("Activation code unavailable");
        UUID orgId = (UUID) row.get("organization_id");
        UUID keyId = (UUID) row.get("key_id");
        String[] scopes = ((java.sql.Array) row.get("scope")).getArray() instanceof String[] s ? s : new String[]{"key:use"};
        String token = randomToken();
        UUID deviceId = UUID.randomUUID();
        jdbc.update("insert into public.key_devices(id,organization_id,key_id,activation_code_id,device_name,device_type,public_key,credential_hash,scopes) values (?,?,?,?,?,?,?,?,?)",
                deviceId, orgId, keyId, activationId, deviceName.trim(), deviceType == null || deviceType.isBlank() ? "phone" : deviceType.trim(), publicKey, sha256(token), scopes);
        jdbc.update("update public.key_activation_codes set used_count=used_count+1 where id=?", activationId);
        return new ActivationResult(deviceId, orgId, keyId, token, Instant.now().plus(365, ChronoUnit.DAYS), orgType(orgId), List.of(scopes));
    }

    public DeviceView authenticateDevice(String token) {
        if (token == null || token.isBlank()) throw new SecurityException("Device credential required");
        return jdbc.queryForObject("select d.id,d.organization_id,d.key_id,d.device_name,d.device_type,d.scopes,d.status,o.org_type from public.key_devices d join public.organizations o on o.id=d.organization_id where d.credential_hash=? and d.status='active'",
                (rs,n) -> new DeviceView((UUID)rs.getObject("id"),(UUID)rs.getObject("organization_id"),(UUID)rs.getObject("key_id"),rs.getString("device_name"),rs.getString("device_type"),array(rs.getArray("scopes")),rs.getString("org_type")), sha256(token));
    }

    public void revokeDevice(UUID userId, UUID orgId, UUID deviceId) {
        requireMember(orgId, userId);
        jdbc.update("update public.key_devices set status='revoked',revoked_at=now() where id=? and organization_id=?", deviceId, orgId);
    }

    public Map<String,Object> capabilities() {
        return Map.of("accountTypes", List.of("personal","organization","enterprise"), "deviceTypes", List.of("phone","tablet","desktop","server","iot","robot","vehicle","industrial","custom"), "keyTypes", List.of("aes-256","rsa-2048","ec-p256","kyber-1024"), "protocols", List.of("HTTPS","WebAuthn/passkeys","mTLS-ready","MQTT-ready","BLE/NFC integration-ready"), "activation", "one-time or bounded-use activation codes");
    }

    private void requireMember(UUID org, UUID user) {
        Integer n = jdbc.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?", Integer.class, org, user);
        if (n == null || n < 1) throw new SecurityException("Organization membership required");
    }
    private String orgType(UUID org) { return jdbc.queryForObject("select org_type from public.organizations where id=?", String.class, org); }
    private String[] normalizeScopes(String[] scopes) { return scopes == null || scopes.length == 0 ? new String[]{"key:use"} : scopes; }
    private String[] array(java.sql.Array a) { try { return a == null ? new String[]{"key:use"} : (String[]) a.getArray(); } catch (Exception e) { return new String[]{"key:use"}; } }
    private String randomCode() { return ("PULSE-" + randomToken().substring(0, 12)).toUpperCase(Locale.ROOT); }
    private String randomToken() { byte[] b = new byte[32]; random.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private String sha256(String s) { try { byte[] b = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); return HexFormat.of().formatHex(b); } catch (Exception e) { throw new IllegalStateException(e); } }

    public record ActivationCode(String code,String prefix,Instant expiresAt,int maxUses,String accountType,List<String> scopes) {}
    public record ActivationResult(UUID deviceId,UUID organizationId,UUID keyId,String deviceToken,Instant tokenExpiresAt,String accountType,List<String> scopes) {}
    public record DeviceView(UUID deviceId,UUID organizationId,UUID keyId,String deviceName,String deviceType,String[] scopes,String accountType) {}
}
