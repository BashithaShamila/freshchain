package com.freshchain.fulfillment.repository;

import com.freshchain.fulfillment.domain.OrderAllocation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderAllocationRepository extends JpaRepository<OrderAllocation, UUID> {

    /**
     * Locked because the allocation event and the confirmation event race to be
     * the one that completes the pair; without the lock both could see an
     * incomplete row and neither would create the shipment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from OrderAllocation a where a.orderId = :orderId")
    Optional<OrderAllocation> lockByOrderId(@Param("orderId") UUID orderId);
}
