package com.ivonix.pulse.ontology.founder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Verifies receipts emitted by FounderActionGate without consuming an approval. */
@Component
public class FounderReceiptVerifier {
  private final byte[] secret;

  public FounderReceiptVerifier(@Value("${pulse.founder.receipt-secret:}") String configuredSecret) {
    String value = Objects.requireNonNull(configuredSecret, "pulse.founder.receipt-secret").trim();
    if (value.length() < 32) throw new IllegalStateException("Founder receipt secret must be at least 32 characters");
    this.secret = value.getBytes(StandardCharsets.UTF_8);
  }

  public boolean verify(UUID confirmationId, UUID organizationId, UUID founderUserId,
                        String action, String resourceType, String resourceId, String receipt) {
    if (confirmationId == null || organizationId == null || founderUserId == null || receipt == null) return false;
    String normalizedAction = normalize(action, 200);
    String normalizedType = normalize(resourceType, 100);
    String normalizedId = normalize(resourceId, 200);
    String contextHash = sha256(organizationId + "|" + normalizedAction + "|" + normalizedType + "|" + normalizedId);
    String expected = hmac(confirmationId + "|" + contextHash + "|" + founderUserId);
    return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), receipt.trim().getBytes(StandardCharsets.US_ASCII));
  }

  private String normalize(String value, int max) {
    String v = Objects.requireNonNull(value, "context").trim();
    if (v.isBlank() || v.length() > max) throw new IllegalArgumentException("Invalid founder receipt context");
    return v;
  }

  private String sha256(String raw) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e) { throw new IllegalStateException("Founder context hashing unavailable", e); }
  }

  private String hmac(String raw) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) { throw new IllegalStateException("Founder receipt verification unavailable", e); }
  }
}
