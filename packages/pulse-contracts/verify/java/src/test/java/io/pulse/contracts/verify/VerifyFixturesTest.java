package io.pulse.contracts.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VerifyFixturesTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path ROOT = Paths.get("../..").toAbsolutePath().normalize();
    private static final Path SCHEMAS = ROOT.resolve("schemas");
    private static final Path FIXTURES = ROOT.resolve("fixtures/v1");
    private static final Path PAYLOADS = FIXTURES.resolve("payloads");
    private static final Pattern TIMESTAMP = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$");

    private static JsonSchema load(String name) {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(SCHEMAS.resolve(name + ".json").toUri());
    }

    @Test
    void allCoreFixturesValidate() throws Exception {
        String[][] cases = {
            {"pulse_event_v1", "pulse_event_v1.golden.json"},
            {"pulse_error_v1", "pulse_error_v1.golden.json"},
            {"pulse_pagination_v1", "pulse_pagination_v1.golden.json"},
            {"pulse_claims_v1", "pulse_claims_v1.golden.json"}
        };
        for (String[] c : cases) {
            JsonNode fixture = MAPPER.readTree(FIXTURES.resolve(c[1]).toFile());
            Set<ValidationMessage> errors = load(c[0]).validate(fixture);
            assertTrue(errors.isEmpty(), c[1] + " failed: " + errors);
        }
    }

    @Test
    void everyPayloadWrapsInEnvelope() throws Exception {
        JsonSchema envelope = load("pulse_event_v1");
        try (Stream<Path> stream = Files.list(PAYLOADS)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".v1.golden.json")).forEach(p -> {
                try {
                    String eventType = p.getFileName().toString().replace(".v1.golden.json", "");
                    JsonNode payload = MAPPER.readTree(p.toFile());
                    var env = MAPPER.createObjectNode();
                    env.put("event_id", "01HZ8X2K5M6P7Q8R9S0T1V2W3X");
                    env.put("event_type", eventType);
                    env.put("event_version", 1);
                    env.put("envelope_version", 1);
                    env.put("occurred_at", "2025-01-15T08:30:00.000Z");
                    env.put("producer", "pulse-core");
                    env.put("aggregate_type", "unknown");
                    env.put("aggregate_id", "01HZ8X2K5M6P7Q8R9S0T1V2W4Y");
                    env.put("org_id", "01HZ8X2K5M6P7Q8R9S0T1V2W5Z");
                    env.put("correlation_id", "01HZ8X2K5M6P7Q8R9S0T1V2W60");
                    env.putNull("causation_id");
                    env.set("payload", payload);
                    env.put("signature", "MEUCIQDf7f8L7dFakedForGoldenFixtureTestingOnlyDoNotUseInProduction");
                    Set<ValidationMessage> errors = envelope.validate(env);
                    assertTrue(errors.isEmpty(), p.getFileName() + " failed: " + errors);
                } catch (Exception e) {
                    throw new AssertionError("Could not verify " + p, e);
                }
            });
        }
    }

    @Test
    void occurredAtHasMilliseconds() throws Exception {
        JsonNode fixture = MAPPER.readTree(FIXTURES.resolve("pulse_event_v1.golden.json").toFile());
        assertTrue(TIMESTAMP.matcher(fixture.get("occurred_at").asText()).matches());
    }
}
