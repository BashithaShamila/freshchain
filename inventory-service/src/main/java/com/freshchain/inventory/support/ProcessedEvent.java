package com.freshchain.inventory.support;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per (event, consumer group). Inserting it is the idempotency check:
 * the insert and the business write share a transaction, so a crash between
 * them rolls back both and the redelivery is handled cleanly.
 */
@Entity
@Table(name = "processed_event")
@IdClass(ProcessedEvent.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Id
    @Column(name = "consumer_group", nullable = false, updatable = false)
    private String consumerGroup;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedEvent(UUID eventId, String consumerGroup) {
        this.eventId = eventId;
        this.consumerGroup = consumerGroup;
        this.processedAt = Instant.now();
    }

    public record Key(UUID eventId, String consumerGroup) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
