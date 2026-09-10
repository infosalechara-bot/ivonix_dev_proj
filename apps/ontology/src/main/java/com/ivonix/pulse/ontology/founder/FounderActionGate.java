package com.ivonix.pulse.ontology.founder;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared enforcement point for critical platform actions.
 * Approvals are bound to exact action/resource context and atomically consumed.
 * Consumption receipts are HMAC-signed with a dedicated runtime secret.
 */
@Component
public class FounderActionGate {
  private final JdbcTemplate jdbc;
  public FounderActionGate(JdbcTemplate jdbc){this.jdbc=jdbc;}

  @Transactional
  public String requireApprovedAndConsume(UUID founderUserId, UUID organizationId,
                                           UUID confirmationId, String action,
                                           String resourceType, String resourceId) {
    Objects.requireNonNull(founderUserId,"founderUserId");
    Objects.requireNonNull(organizationId,"organizationId");
    Objects.requireNonNull(confirmationId,"confirmationId");
    String normalizedAction = normalize(action, 200);
    String normalizedType = normalize(resourceType, 100);
    String normalizedId = normalize(resourceId, 200);
    String contextHash = sha256(organizationId + "|" + normalizedAction + "|" + normalizedType + "|" + normalizedId);
    String receiptPayload = confirmationId + "|" + contextHash + "|" + founderUserId;
    String receipt = hmacReceipt(receiptPayload);

    int changed=jdbc.update("update public.executive_confirmations set consumed_at=now(), consumed_by=?, consumed_action=?, consumed_resource_type=?, consumed_resource_id=?, consumption_receipt_hash=? where id=? and organization_id=? and requested_by=? and status='approved' and consumed_at is null and expires_at>now() and requested_action=? and approved_action_hash=?",
        founderUserId, normalizedAction, normalizedType, normalizedId, receipt,
        confirmationId, organizationId, founderUserId, normalizedAction, contextHash);
    if(changed!=1) throw new SecurityException("Critical action requires a valid, unexpired, context-bound founder approval");
    return receipt;
  }

  private String normalize(String value, int max) {
    String v=Objects.requireNonNull(value,"context").trim();
    if(v.isBlank() || v.length()>max) throw new IllegalArgumentException("Invalid founder approval context");
    return v;
  }

  private String sha256(String raw) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch(Exception e) { throw new IllegalStateException("Founder context hashing unavailable",e); }
  }

  private String hmacReceipt(String payload) {
    String secret = System.getenv("PULSE_FOUNDER_RECEIPT_SECRET");
    if(secret == null || secret.length() < 32) {
      throw new IllegalStateException("PULSE_FOUNDER_RECEIPT_SECRET must be configured with at least 32 characters");
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch(Exception e) { throw new IllegalStateException("Founder receipt signing unavailable",e); }
  }
}
