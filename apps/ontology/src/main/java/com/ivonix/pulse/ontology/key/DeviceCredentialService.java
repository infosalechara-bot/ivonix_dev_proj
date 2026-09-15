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

/** Human-authorized device credential rotation. Old credentials are invalid immediately. */
@Service
public class DeviceCredentialService {
    private static final int TOKEN_DAYS = 30;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public DeviceCredentialService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public RotationResult rotate(UUID userId, UUID organizationId, UUID deviceId) {
        requireMember(userId, organizationId);
        String token = randomToken();
        int updated = jdbc.update("update public.key_devices set credential_hash=?,last_seen_at=null where id=? and organization_id=? and status='active'",
                sha256(token), deviceId, organizationId);
        if (updated != 1) throw new SecurityException("Device unavailable");
        return new RotationResult(deviceId, organizationId, token, Instant.now().plus(TOKEN_DAYS, ChronoUnit.DAYS), TOKEN_DAYS);
    }

    private void requireMember(UUID userId, UUID organizationId) {
        Integer count = jdbc.queryForObject("select count(*) from public.organization_members where organization_id=? and user_id=?", Integer.class, organizationId, userId);
        if (count == null || count < 1) throw new SecurityException("Organization membership required");
    }

    private String randomToken() { byte[] b = new byte[32]; random.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("Credential hashing unavailable", e); } }
    public record RotationResult(UUID deviceId, UUID organizationId, String deviceToken, Instant tokenExpiresAt, int lifetimeDays) {}
}
