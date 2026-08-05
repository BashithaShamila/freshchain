package com.freshchain.inventory.repository;

import com.freshchain.inventory.domain.Reservation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.orderId = :orderId")
    Optional<Reservation> lockByOrderId(@Param("orderId") UUID orderId);

    /**
     * Expiry sweep. Here SKIP LOCKED is exactly right, and for the opposite
     * reason it was wrong for allocation: a row another replica has already
     * claimed does not need this replica's attention, and skipping it keeps the
     * sweep moving instead of queueing behind a peer.
     */
    @Query(value = """
            SELECT * FROM reservation
             WHERE status = 'HELD'
               AND expires_at < :now
             ORDER BY expires_at ASC
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Reservation> lockExpiredHeldReservations(@Param("now") Instant now,
                                                   @Param("batchSize") int batchSize);
}
