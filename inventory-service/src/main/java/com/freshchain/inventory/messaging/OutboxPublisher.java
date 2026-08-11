package com.freshchain.inventory.messaging;

import com.freshchain.inventory.config.OutboxProperties;
import com.freshchain.inventory.repository.OutboxRepository;
import com.freshchain.inventory.support.OutboxEntry;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the outbox onto Kafka.
 *
 * <p>Deliberately <b>not</b> wrapped in a ShedLock. The claim query already uses
 * {@code FOR UPDATE SKIP LOCKED}, so several replicas can drain the table at the
 * same time without any of them publishing the same row twice; a scheduler lock
 * would take that parallelism away and buy nothing. The expiry sweeper is the
 * opposite case and does take one.
 *
 * <p>Guarantees at-least-once delivery with sub-second lag. Debezium reading the
 * WAL would remove the polling entirely, at the cost of a Kafka Connect cluster
 * to run and operate — see docs/adr/ADR-003 for when that trade flips.
 */
@Component
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;
    private final MeterRegistry meterRegistry;

    public OutboxPublisher(OutboxRepository outbox,
                           KafkaTemplate<String, String> kafka,
                           OutboxProperties properties,
                           MeterRegistry meterRegistry) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("freshchain.outbox.pending", outbox, OutboxRepository::countByPublishedAtIsNull);
    }

    @Scheduled(fixedDelayString = "${freshchain.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEntry> batch = outbox.lockUnpublished(properties.batchSize());
        if (batch.isEmpty()) {
            return;
        }

        // Fire every send first, then wait. The producer batches them, so this is
        // one round trip for the batch rather than one per row.
        List<PendingSend> inFlight = new ArrayList<>(batch.size());
        for (OutboxEntry entry : batch) {
            inFlight.add(new PendingSend(entry, kafka.send(recordFor(entry))));
        }

        int published = 0;
        for (PendingSend pending : inFlight) {
            try {
                pending.future().join();
                // Marked only after the broker has acknowledged. A crash before
                // this point leaves the row unpublished and the next poll resends
                // it — at-least-once, which the consumers are built to absorb.
                pending.entry().markPublished();
                published++;
            } catch (RuntimeException e) {
                log.warn("could not publish outbox row {} ({} -> {}); will retry on the next poll",
                        pending.entry().getId(), pending.entry().getEventType(),
                        pending.entry().getTopic(), e);
            }
        }

        meterRegistry.counter("freshchain.outbox.published").increment(published);
        if (published > 0) {
            log.debug("published {} of {} outbox row(s)", published, batch.size());
        }
    }

    /**
     * Retention. Published rows are kept for a while as a debugging record, then
     * deleted — without this the table grows without bound. Unpublished rows are
     * never eligible, whatever their age: publication is still owed. Like the
     * drain above this needs no ShedLock; a concurrent delete of the same rows
     * is merely a no-op for whoever arrives second.
     */
    @Scheduled(fixedDelayString = "${freshchain.outbox.prune-interval-ms:3600000}")
    @Transactional
    public void prunePublished() {
        Instant cutoff = Instant.now().minus(properties.retention());
        int removed = outbox.deletePublishedBefore(cutoff);
        if (removed > 0) {
            log.info("pruned {} outbox row(s) published before {}", removed, cutoff);
        }
    }

    /**
     * Keyed on the aggregate id, and carrying the originating request's
     * traceparent so the consumer joins that trace instead of opening a new one.
     */
    private static ProducerRecord<String, String> recordFor(OutboxEntry entry) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                entry.getTopic(), entry.getAggregateId().toString(), entry.getPayload());
        if (entry.getTraceparent() != null) {
            record.headers().add("traceparent",
                    entry.getTraceparent().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private record PendingSend(OutboxEntry entry, CompletableFuture<SendResult<String, String>> future) {
    }
}
