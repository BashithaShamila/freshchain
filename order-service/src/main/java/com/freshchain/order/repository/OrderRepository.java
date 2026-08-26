package com.freshchain.order.repository;

import com.freshchain.order.domain.Order;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Locked because two things race for an order: the customer confirming it,
     * and an inventory event landing on it. Whichever gets there first wins
     * cleanly instead of both reading the same stale status.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId = :orderId")
    Optional<Order> lockById(@Param("orderId") UUID orderId);

    List<Order> findByCustomerIdOrderByPlacedAtDesc(UUID customerId, Pageable pageable);
}
