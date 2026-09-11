package com.ivonix.pulse.ontology.eventbus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Canonical PULSE Universal Event Envelope v1 adapter. */
final class CanonicalPulseEvent {
    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final DateTimeFormatter UTC_MILLIS = DateTimeFormatter.ISO_INSTANT;

    private CanonicalPulseEvent() {}

    static Map<String, Object> build(ObjectMapper mapper, byte[] signingKey, String eventType, String producer, UUID organizationId, String correlationId, Map<String, Object> payload, UUID legacyEventId, Instant occurredAt) {
        if (eventType == null || !eventType.matches("^[a-z][a-z0-9]*(\\.[a-z0-9]+)+$")) throw new IllegalArgumentException("Event type is not canonical: " + eventType);
        String eventId = ulid(occurredAt.toEpochMilli(), legacyEventId);
        String aggregateType = payload != null && payload.get("deviceId") != null ? "Machine" : "Event";
        String aggregateId = payload != null && payload.get("deviceId") != null ? String.valueOf(payload.get("deviceId")) : legacyEventId.toString();
        String correlation = correlationId == null || correlationId.isBlank() ? legacyEventId.toString() : correlationId;
        Map<String, Object> unsigned = new LinkedHashMap<>();
        unsigned.put("eventId", eventId);
        unsigned.put("eventType", eventType);
        unsigned.put("eventVersion", 1);
        unsigned.put("envelopeVersion", 1);
        unsigned.put("occurredAt", formatInstant(occurredAt));
        unsigned.put("producer", producer);
        unsigned.put("aggregateType", aggregateType);
        unsigned.put("aggregateId", aggregateId);
        unsigned.put("organizationId", organizationId == null ? null : organizationId.toString());
        unsigned.put("correlationId", correlation);
        unsigned.put("causationId", null);
        unsigned.put("payload", payload == null ? Map.of() : payload);
        String canonicalJson = write(mapper, unsigned);
        String signature = "sha256=" + hmacHex(signingKey, canonicalJson);
        Map<String, Object> envelope = new LinkedHashMap<>(unsigned);
        envelope.put("signature", signature);
        return envelope;
    }

    private static String formatInstant(Instant instant) {
        String value = UTC_MILLIS.format(instant);
        int dot = value.indexOf('.');
        if (dot < 0) return value.replace("Z", ".000Z");
        int end = value.length() - 1;
        String fraction = value.substring(dot + 1, end);
        if (fraction.length() > 3) fraction = fraction.substring(0, 3);
        while (fraction.length() < 3) fraction += "0";
        return value.substring(0, dot + 1) + fraction + "Z";
    }

    private static String write(ObjectMapper mapper, Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Unable to serialize canonical event", e); }
    }

    private static String hmacHex(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Canonical event signing unavailable", e); }
    }

    private static String ulid(long millis, UUID source) {
        byte[] random = ByteBuffer.allocate(16).putLong(source.getMostSignificantBits()).putLong(source.getLeastSignificantBits()).array();
        StringBuilder out = new StringBuilder(26);
        for (int shift = 40; shift >= 0; shift -= 5) out.append(CROCKFORD[(int) ((millis >>> shift) & 31)]);
        int buffer = 0, bits = 0;
        for (byte b : random) {
            buffer = (buffer << 8) | (b & 0xff); bits += 8;
            while (bits >= 5 && out.length() < 26) { bits -= 5; out.append(CROCKFORD[(buffer >>> bits) & 31]); }
        }
        while (out.length() < 26) out.append('0');
        return out.toString();
    }
}
