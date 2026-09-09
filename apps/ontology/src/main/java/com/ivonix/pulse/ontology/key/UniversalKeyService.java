package com.ivonix.pulse.ontology.key;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class UniversalKeyService {
    private static final Set<String> ALLOWED_SCOPES = Set.of("key:use");
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public UniversalKeyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public ActivationCode createActivationCode(UUID userId, UUID orgId, UUID keyId, int ttlMinutes, int maxUses, String[] scopes) {
        requireMember(orgId, userId);
        if (ttlMinutes < 1 || ttlMinutes > 1440) throw new IllegalArgumentException("ttlMinutes must be 1..1440");
        if (maxUses < 1 || maxUses > 100) throw new IllegalArgumentException("maxUses must be 1..100");
        if (keyId != null) jdbc.queryForObject("select id from public.crypto_keys where id=? and organization_id=? and status='active' and key_type='aes-256' and purpose='encryption'", UUID.class, keyId, orgId);
        String[] normalized = normalizeScopes(scopes);
        String code = randomCode();
        Instant expires = Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES);
        jdbc.update("insert into public.key_activation_codes(organization_id,key_id,code_hash,code_prefix,scope,max_uses,expires_at,created_by) values (?,?,?,?,?,?,?,?)",
                orgId, keyId, sha256(code), code.substring(0, 4), normalized, maxUses, expires, userId);
        return new ActivationCode(code, code.substring(0, 4), expires, maxUses, orgType(orgId), List.of(normalized));
    }

    @Transactional
    public ActivationResult activate(String code, String deviceName, String deviceType, String publicKey) {
        if (code == null || code.isBlank()) throw new SecurityException("Activation code required");
        if (deviceName == null || deviceName.isBlank()) throw new IllegalArgumentException("deviceName required");
        Map<String, Object> row = jdbc.queryForMap("select id,organization_id,key_id,scope,max_uses,used_count,expires_at,revoked_at from public.key_activation_codes where code_hash=? for update", sha256(code.trim().toUpperCase(Locale.ROOT)));
        UUID activationId = (UUID) row.get("id");
        Instant expires = toInstant(row.get("expires_at"));
        int used = ((Number) row.get("used_count")).intValue();
        int max = ((Number) row.get("max_uses")).intValue();
        if (row.get("revoked_at") != null || !expires.isAfter(Instant.now()) || used >= max) throw new SecurityException("Activation code unavailable");
        UUID orgId = (UUID) row.get("organization_id");
        UUID keyId = (UUID) row.get("key_id");
        String[] scopes = array((java.sql.Array) row.get("scope"));
        for (String scope : scopes) if (!ALLOWED_SCOPES.contains(scope)) throw new SecurityException("Activation scope is not permitted");
        if (keyId != null) jdbc.queryForObject("select id from public.crypto_keys where id=? and organization_id=? and status='active' and key_type='aes-256' and purpose='encryption'", UUID.class, keyId, orgId);
        String token = randomToken();
        UUID deviceId = UUID.randomUUID();
        jdbc.update("insert into public.key_devices(id,organization_id,key_id,activation_code_id,device_name,device_type,public_key,credential_hash,scopes) values (?,?,?,?,?,?,?,?,?)",
                deviceId, orgId, keyId, activationId, deviceName.trim(), deviceType == null || deviceType.isBlank() ? "custom" : deviceType.trim(), publicKey, sha256(token), scopes);
        int updated = jdbc.update("update public.key_activation_codes set used_count=used_count+1 where id=? and used_count < max_uses", activationId);
        if (updated != 1) throw new SecurityException("Activation code unavailable");
        return new ActivationResult(deviceId, orgId, keyId, token, Instant.now().plus(90, ChronoUnit.DAYS), orgType(orgId), List.of(scopes));
    }

    public DeviceView authenticateDevice(String token) {
        if (token == null || token.isBlank()) throw new SecurityException("Device credential required");
        return jdbc.queryForObject("select d.id,d.organization_id,d.key_id,d.device_name,d.device_type,d.scopes,d.status,o.org_type from public.key_devices d join public.organizations o on o.id=d.organization_id where d.credential_hash=? and d.status='active'", (rs,n)->new DeviceView((UUID)rs.getObject("id"),(UUID)rs.getObject("organization_id"),(UUID)rs.getObject("key_id"),rs.getString("device_name"),rs.getString("device_type"),array(rs.getArray("scopes")),rs.getString("org_type")), sha256(token));
    }

    public ClientCredential createClient(UUID userId, UUID orgId, String name, String clientType, String[] scopes) {
        requireMember(orgId, userId);
        String cid = "pk_" + randomToken().substring(0, 20);
        String secret = randomToken();
        String[] normalized = normalizeScopes(scopes);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into public.key_api_clients(id,organization_id,name,client_type,client_id,client_secret_hash,scopes,created_by) values (?,?,?,?,?,?,?,?)", id, orgId, name, clientType == null || clientType.isBlank() ? orgType(orgId) : clientType, cid, sha256(secret), normalized, userId);
        return new ClientCredential(id, cid, secret, List.of(normalized), orgType(orgId));
    }

    public ApiToken issueClientToken(String clientId, String clientSecret) {
        if (clientId == null || clientSecret == null) throw new SecurityException("Client credentials required");
        Map<String,Object> c = jdbc.queryForMap("select id,organization_id,scopes,status from public.key_api_clients where client_id=?", clientId);
        if (!"active".equals(c.get("status")) || !sha256(clientSecret).equals(secretHash(clientId))) throw new SecurityException("Invalid client credentials");
        UUID tokenId=UUID.randomUUID(); String token=randomToken(); Instant expires=Instant.now().plus(1,ChronoUnit.HOURS); UUID org=(UUID)c.get("organization_id"); String[] scopes=array((java.sql.Array)c.get("scopes"));
        jdbc.update("insert into public.key_api_tokens(id,organization_id,client_id,token_hash,scopes,expires_at) values (?,?,?,?,?,?)",tokenId,org,(UUID)c.get("id"),sha256(token),scopes,expires);
        return new ApiToken(token,expires,org,List.of(scopes));
    }

    public TokenView authenticateApiToken(String token) {
        if (token == null || token.isBlank()) throw new SecurityException("API token required");
        return jdbc.queryForObject("select id,organization_id,client_id,device_id,scopes,expires_at from public.key_api_tokens where token_hash=? and revoked_at is null and expires_at>now()", (rs,n)->new TokenView((UUID)rs.getObject("id"),(UUID)rs.getObject("organization_id"),(UUID)rs.getObject("client_id"),(UUID)rs.getObject("device_id"),array(rs.getArray("scopes")),rs.getObject("expires_at",Instant.class)), sha256(token));
    }

    public void revokeDevice(UUID userId,UUID orgId,UUID deviceId){requireMember(orgId,userId);jdbc.update("update public.key_devices set status='revoked',revoked_at=now() where id=? and organization_id=?",deviceId,orgId);}
    public Map<String,Object> capabilities(){return Map.of("accountTypes",List.of("personal","organization","enterprise"),"deviceTypes",List.of("phone","tablet","desktop","server","iot","robot","vehicle","industrial","custom"),"keyTypes",List.of("aes-256"),"keyPurposes",List.of("encryption"),"protocols",List.of("HTTPS"),"activation","one-time or bounded-use activation codes","apiAuth",List.of("client credentials","device credentials"));}
    private String secretHash(String clientId){return jdbc.queryForObject("select client_secret_hash from public.key_api_clients where client_id=?",String.class,clientId);}
    private void requireMember(UUID org,UUID user){Integer n=jdbc.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?",Integer.class,org,user);if(n==null||n<1)throw new SecurityException("Organization membership required");}
    private String orgType(UUID org){return jdbc.queryForObject("select org_type from public.organizations where id=?",String.class,org);}
    private String[] normalizeScopes(String[] scopes){if(scopes==null||scopes.length==0)return new String[]{"key:use"};String[] normalized=Arrays.stream(scopes).filter(Objects::nonNull).map(String::trim).filter(ALLOWED_SCOPES::contains).distinct().toArray(String[]::new);if(normalized.length==0)throw new IllegalArgumentException("At least one supported scope is required");return normalized;}
    private String[] array(java.sql.Array a){try{return a==null?new String[]{"key:use"}:(String[])a.getArray();}catch(Exception e){throw new IllegalStateException("Invalid scope storage",e);}}
    private Instant toInstant(Object value){if(value instanceof Instant i)return i;if(value instanceof java.sql.Timestamp t)return t.toInstant();if(value instanceof java.time.OffsetDateTime o)return o.toInstant();if(value instanceof java.time.LocalDateTime l)return l.toInstant(java.time.ZoneOffset.UTC);throw new IllegalStateException("Unsupported timestamp type");}
    private String randomCode(){return ("PULSE-"+randomToken().substring(0,12)).toUpperCase(Locale.ROOT);}
    private String randomToken(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record ActivationCode(String code,String prefix,Instant expiresAt,int maxUses,String accountType,List<String> scopes){}
    public record ActivationResult(UUID deviceId,UUID organizationId,UUID keyId,String deviceToken,Instant tokenExpiresAt,String accountType,List<String> scopes){}
    public record DeviceView(UUID deviceId,UUID organizationId,UUID keyId,String deviceName,String deviceType,String[] scopes,String accountType){}
    public record ClientCredential(UUID id,String clientId,String clientSecret,List<String> scopes,String accountType){}
    public record ApiToken(String accessToken,Instant expiresAt,UUID organizationId,List<String> scopes){}
    public record TokenView(UUID tokenId,UUID organizationId,UUID clientId,UUID deviceId,String[] scopes,Instant expiresAt){}
}
