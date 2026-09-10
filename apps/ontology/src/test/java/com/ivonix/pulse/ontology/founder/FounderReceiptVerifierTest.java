package com.ivonix.pulse.ontology.founder;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FounderReceiptVerifierTest {
  @Test
  void rejectsMissingOrMalformedReceiptContext() {
    assertThrows(IllegalStateException.class, () -> new FounderReceiptVerifier("short"));
  }

  @Test
  void verifiesOnlyExactContextWithConfiguredSecret() {
    String secret = "0123456789abcdef0123456789abcdef";
    FounderReceiptVerifier verifier = new FounderReceiptVerifier(secret);
    UUID confirmation = UUID.randomUUID();
    UUID org = UUID.randomUUID();
    UUID founder = UUID.randomUUID();

    assertFalse(verifier.verify(confirmation, org, founder, "rotate-key", "key", "k1", "bad"));
    assertFalse(verifier.verify(confirmation, org, founder, "rotate-key", "key", "k2", "bad"));
  }
}
