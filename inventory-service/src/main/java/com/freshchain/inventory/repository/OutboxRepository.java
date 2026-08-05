package com.freshchain.inventory.repository;

import com.freshchain.inventory.support.OutboxEntry;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {

    /**
     * SKIP LOCKED lets several replicas drain the outbox in parallel without
     * publishing the same row twice and without blocking each other.
     */
    @Query(value = """
            SELECT * FROM outbox
             WHERE published_at IS NULL
             ORDER BY id ASC
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEntry> lockUnpublished(@Param("batchSize") int batchSize);

    long countByPublishedAtIsNull();

    /**
     * Retention: a published row has done its job and is kept only as a debugging
     * record. Rows still awaiting publication are never touched, whatever their age.
     */
    @Modifying
    @Query("DELETE FROM OutboxEntry e WHERE e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
