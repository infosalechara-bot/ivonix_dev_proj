package com.ivonix.pulse.ontology.engineering;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Runtime guard for the versioned diagnostic_result_v1 contract. */
public final class DiagnosticResultContract {
    public static final String VERSION = "1.0";
    private static final Set<String> ENGINE_TYPES = Set.of("RULE_ENGINE", "STATISTICAL_MODEL", "ML_MODEL", "FOUNDATION_MODEL", "HYBRID_MODEL", "HUMAN_REVIEW");

    private DiagnosticResultContract() {}

    public static void validate(Map<String,Object> result) {
        if (result == null) throw new IllegalStateException("Diagnostic response is null");
        require(result, "schemaVersion", String.class);
        if (!VERSION.equals(result.get("schemaVersion"))) throw new IllegalStateException("Unsupported diagnostic schema version");
        String engine = string(result, "engineType");
        if (!ENGINE_TYPES.contains(engine)) throw new IllegalStateException("Unsupported diagnostic engine type");
        requireNonBlank(result, "modelId");
        requireNonBlank(result, "modelVersion");
        requireNonBlank(result, "probableFault");
        Object confidence = result.get("confidence");
        if (!(confidence instanceof Number n) || n.doubleValue() < 0 || n.doubleValue() > 1) throw new IllegalStateException("Diagnostic confidence outside [0,1]");
        if (!(result.get("recommendedActions") instanceof List<?>)) throw new IllegalStateException("recommendedActions must be an array");
        if (!(result.get("doNotDisassemble") instanceof Boolean)) throw new IllegalStateException("doNotDisassemble must be boolean");
        if (!(result.get("evidence") instanceof List<?>)) throw new IllegalStateException("evidence must be an array");
    }

    private static void require(Map<String,Object> m, String key, Class<?> type) {
        if (!type.isInstance(m.get(key))) throw new IllegalStateException(key + " has invalid type");
    }
    private static void requireNonBlank(Map<String,Object> m, String key) {
        if (!(m.get(key) instanceof String s) || s.isBlank()) throw new IllegalStateException(key + " is required");
    }
    private static String string(Map<String,Object> m, String key) {
        require(m, key, String.class);
        return (String)m.get(key);
    }
}
