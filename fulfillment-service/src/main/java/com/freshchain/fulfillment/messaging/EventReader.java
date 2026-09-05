package com.freshchain.fulfillment.messaging;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshchain.events.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Turns a raw record value into a typed envelope.
 *
 * <p>Records are carried as plain strings and deserialised here rather than by a
 * JsonDeserializer configured with type headers. It costs a few lines and buys
 * back control: a producer cannot make this consumer instantiate a class by
 * putting a type name in a header, and the failure mode for a malformed payload
 * is one this code chooses.
 */
@Component
@RequiredArgsConstructor
public class EventReader {

    private final ObjectMapper objectMapper;

    public <T> EventEnvelope<T> read(String json, Class<T> payloadType) {
        JavaType type = objectMapper.getTypeFactory()
                .constructParametricType(EventEnvelope.class, payloadType);
        try {
            EventEnvelope<T> envelope = objectMapper.readValue(json, type);
            if (envelope.schemaVersion() > EventEnvelope.CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException(
                        "event %s is schema v%d but this consumer understands v%d"
                                .formatted(envelope.eventType(), envelope.schemaVersion(),
                                        EventEnvelope.CURRENT_SCHEMA_VERSION));
            }
            return envelope;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // IllegalArgumentException is registered as non-retryable, so a
            // payload this consumer can never parse goes straight to the DLT
            // instead of blocking its partition for three rounds of backoff.
            throw new IllegalArgumentException("unreadable " + payloadType.getSimpleName() + " payload", e);
        }
    }
}
