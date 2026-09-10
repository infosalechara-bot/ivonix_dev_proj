package com.ivonix.pulse.ontology.reclaim;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryFounderGateContractTest {
  private static final Path ROOT = Path.of("src/main/java/com/ivonix/pulse/ontology/reclaim");

  @Test
  void recoveryRequiresFounderConfirmationAndConsumesItBeforeJobCreation() throws Exception {
    String request = Files.readString(ROOT.resolve("RecoveryJobRequest.java"));
    String service = Files.readString(ROOT.resolve("RecoveryService.java"));

    assertTrue(request.contains("UUID founderConfirmationId"));
    assertTrue(request.contains("@NotNull UUID founderConfirmationId"));
    assertTrue(service.contains("FounderActionGate"));
    assertTrue(service.contains("founderActionGate.requireApprovedAndConsume"));
    assertTrue(service.contains("FOUNDER_ACTION = \"recovery.execute\""));
    assertTrue(service.contains("FOUNDER_RESOURCE_TYPE = \"recovery_job\""));
    assertTrue(service.contains("r.founderConfirmationId()"));

    int gate = service.indexOf("founderActionGate.requireApprovedAndConsume");
    int insert = service.indexOf("insert into recovery_jobs");
    assertTrue(gate >= 0 && insert >= 0 && gate < insert,
        "FND-026 consumption must precede recovery job creation");
  }
}
