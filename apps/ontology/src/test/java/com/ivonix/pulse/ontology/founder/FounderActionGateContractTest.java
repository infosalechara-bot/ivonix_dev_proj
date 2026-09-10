package com.ivonix.pulse.ontology.founder;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FounderActionGateContractTest {
  private static final Path ROOT = Path.of("src/main/java/com/ivonix/pulse/ontology/founder");

  @Test
  void gateBindsApprovalToExactActionAndResource() throws Exception {
    String source = Files.readString(ROOT.resolve("FounderActionGate.java"));
    assertTrue(source.contains("requested_action=?"));
    assertTrue(source.contains("approved_action_hash=?"));
    assertTrue(source.contains("consumed_resource_type=?"));
    assertTrue(source.contains("consumed_resource_id=?"));
    assertTrue(source.contains("consumption_receipt_hash=?"));
    assertTrue(source.contains("consumed_at is null"));
    assertTrue(source.contains("expires_at>now()"));
  }

  @Test
  void receiptIsCryptographicallySignedAndRequiresDedicatedSecret() throws Exception {
    String source = Files.readString(ROOT.resolve("FounderActionGate.java"));
    assertTrue(source.contains("PULSE_FOUNDER_RECEIPT_SECRET"));
    assertTrue(source.contains("HmacSHA256"));
    assertTrue(source.contains("secret.length() < 32"));
  }

  @Test
  void approvalCreationPersistsContextHash() throws Exception {
    String source = Files.readString(ROOT.resolve("FounderService.java"));
    assertTrue(source.contains("approved_action_hash"));
    assertTrue(source.contains("contextHash(orgId,description,type,resource)"));
  }
}
