package com.freshchain.fulfillment.repository;

import com.freshchain.fulfillment.support.ProcessedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.Key> {

    /**
     * {@code ON CONFLICT DO NOTHING} rather than catching a
     * DuplicateKeyException on purpose: in Postgres a constraint violation
     * marks the whole transaction as failed, so "insert and catch" would poison
     * the very transaction the handler still needs in order to commit. Returning
     * 0 rows lets the caller detect the replay and return cleanly.
     *
     * @return 1 if this event is new to the group, 0 if it is a replay
     */
    @Modifying
    @Query(value = """
            INSERT INTO processed_event (event_id, consumer_group, processed_at)
            VALUES (:eventId, :consumerGroup, now())
            ON CONFLICT (event_id, consumer_group) DO NOTHING
            """, nativeQuery = true)
    int tryClaim(@Param("eventId") UUID eventId, @Param("consumerGroup") String consumerGroup);
}
