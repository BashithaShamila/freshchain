package com.freshchain.inventory.repository;

import com.freshchain.inventory.domain.InventoryLot;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryLotRepository extends JpaRepository<InventoryLot, UUID> {

    /**
     * The allocation query. Two things about it are load-bearing.
     *
     * <p><b>PESSIMISTIC_WRITE (a blocking {@code SELECT ... FOR UPDATE}), not
     * SKIP LOCKED.</b> Under contention a second transaction waits rather than
     * skipping the locked lot. Skipping would be faster, but on a single
     * contended lot it makes every concurrent caller see "no stock" and get a
     * spurious rejection while units are still on the shelf — and it silently
     * breaks strict FEFO by hopping to a later-expiring lot. Correct allocation
     * beats throughput here. See docs/adr/ADR-002.
     *
     * <p><b>Deterministic ORDER BY.</b> Expiry, then receipt, then id. Beyond
     * being the FEFO rule, it fixes a global lock-acquisition order, so two
     * concurrent multi-line orders touching the same lots cannot deadlock by
     * grabbing them in opposite sequences.
     *
     * <p>Postgres re-checks the WHERE clause after a blocked lock is granted, so
     * a lot another transaction has just exhausted drops out of the result
     * instead of being handed out twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from InventoryLot l
             where l.sku = :sku
               and l.warehouseId = :warehouseId
               and l.expiryDate > :today
               and l.qtyOnHand > l.qtyReserved
             order by l.expiryDate asc, l.receivedAt asc, l.lotId asc
            """)
    List<InventoryLot> lockAllocatableLots(@Param("sku") String sku,
                                           @Param("warehouseId") String warehouseId,
                                           @Param("today") LocalDate today);

    /** Locks specific lots for release/consume, in id order to keep lock ordering stable. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from InventoryLot l where l.lotId in :lotIds order by l.lotId asc")
    List<InventoryLot> lockByIds(@Param("lotIds") List<UUID> lotIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from InventoryLot l where l.lotId = :lotId")
    java.util.Optional<InventoryLot> lockById(@Param("lotId") UUID lotId);

    /** Read-only view for the availability endpoint. No locks taken. */
    @Query("""
            select l from InventoryLot l
             where l.sku = :sku
               and (:warehouseId is null or l.warehouseId = :warehouseId)
             order by l.expiryDate asc, l.receivedAt asc
            """)
    List<InventoryLot> findForAvailability(@Param("sku") String sku,
                                           @Param("warehouseId") String warehouseId);

    @Query("""
            select coalesce(sum(l.qtyOnHand - l.qtyReserved), 0) from InventoryLot l
             where l.sku = :sku
               and l.warehouseId = :warehouseId
               and l.expiryDate > :today
            """)
    long availableQuantity(@Param("sku") String sku,
                           @Param("warehouseId") String warehouseId,
                           @Param("today") LocalDate today);
}
