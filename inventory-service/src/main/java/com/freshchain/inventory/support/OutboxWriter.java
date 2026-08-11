package com.freshchain.inventory.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshchain.events.Actor;
import com.freshchain.events.EventEnvelope;
import com.freshchain.inventory.repository.OutboxRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The only way this service emits an event. Callers hand over a payload; the
 * envelope, the trace id and the durable row are handled here, inside whatever
 * transaction the caller already has open.
 */
@Component
@Slf4j
public class OutboxWriter {

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<Tracer> tracer;

    public OutboxWriter(OutboxRepository outbox, ObjectMapper objectMapper, ObjectProvider<Tracer> tracer) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    public <T> void write(String aggregateType, UUID aggregateId, String eventType,
                          String topic, T payload, Actor actor) {
        Span span = currentSpan();
        EventEnvelope<T> envelope = EventEnvelope.of(
                eventType, aggregateId, span == null ? null : span.context().traceId(), actor, payload);
        try {
            String json = objectMapper.writeValueAsString(envelope);
            outbox.save(OutboxEntry.pending(
                    aggregateType, aggregateId, eventType, topic, json, traceparentOf(span)));
            log.debug("queued {} for {} on {}", eventType, aggregateId, topic);
        } catch (JsonProcessingException e) {
            // Serialising our own record failed: the payload shape is wrong, which
            // is a bug, not a transient fault. Fail the transaction loudly.
            throw new IllegalStateException("could not serialise " + eventType + " for " + aggregateId, e);
        }
    }

    private Span currentSpan() {
        Tracer active = tracer.getIfAvailable();
        return active == null ? null : active.currentSpan();
    }

    /**
     * Renders the current span as a W3C traceparent, sampled. The publisher puts
     * this back on the Kafka record so the consumer continues this trace rather
     * than starting a fresh one an hour of wall-clock later.
     */
    private static String traceparentOf(Span span) {
        if (span == null) {
            return null;
        }
        return "00-%s-%s-01".formatted(span.context().traceId(), span.context().spanId());
    }
}
