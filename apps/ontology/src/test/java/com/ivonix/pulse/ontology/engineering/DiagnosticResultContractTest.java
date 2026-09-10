package com.ivonix.pulse.ontology.engineering;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticResultContractTest {
    private Map<String,Object> valid() {
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("schemaVersion", "1.0");
        r.put("engineType", "RULE_ENGINE");
        r.put("modelId", "pulse-diagnostic-rules");
        r.put("modelVersion", "1.0");
        r.put("probableFault", "none");
        r.put("confidence", 0.1);
        r.put("recommendedActions", List.of("continue telemetry"));
        r.put("doNotDisassemble", true);
        r.put("evidence", List.of());
        return r;
    }

    @Test void acceptsCanonicalResult() { assertDoesNotThrow(() -> DiagnosticResultContract.validate(valid())); }
    @Test void rejectsSnakeCaseCompatibilityPayload() { var r=valid(); r.remove("recommendedActions"); r.put("recommended_actions", List.of()); assertThrows(IllegalStateException.class, () -> DiagnosticResultContract.validate(r)); }
    @Test void rejectsBadConfidence() { var r=valid(); r.put("confidence", 2); assertThrows(IllegalStateException.class, () -> DiagnosticResultContract.validate(r)); }
    @Test void rejectsUnknownEngine() { var r=valid(); r.put("engineType", "MAGIC_MODEL"); assertThrows(IllegalStateException.class, () -> DiagnosticResultContract.validate(r)); }
    @Test void rejectsWrongVersion() { var r=valid(); r.put("schemaVersion", "2.0"); assertThrows(IllegalStateException.class, () -> DiagnosticResultContract.validate(r)); }
}
