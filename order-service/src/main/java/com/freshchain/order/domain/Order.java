package com.freshchain.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The order aggregate. Every state transition on it lives on this class. */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private String warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "allow_partial", nullable = false, updatable = false)
    private boolean allowPartial;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "substitution_advice")
    private String substitutionAdvice;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    // Batched so that listing N orders costs two queries rather than N + 1.
    @org.hibernate.annotations.BatchSize(size = 50)
    private List<OrderLine> lines = new ArrayList<>();

    public static Order place(UUID customerId, String warehouseId, boolean allowPartial) {
        Order order = new Order();
        Instant now = Instant.now();
        order.orderId = UUID.randomUUID();
        order.customerId = customerId;
        order.warehouseId = warehouseId;
        order.allowPartial = allowPartial;
        order.status = OrderStatus.PENDING_ALLOCATION;
        order.placedAt = now;
        order.updatedAt = now;
        return order;
    }

    public void addLine(String sku, int qty, BigDecimal unitPrice) {
        lines.add(OrderLine.of(this, sku, qty, unitPrice));
    }

    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /**
     * Records what inventory managed to allocate.
     *
     * <p>Guarded against arriving out of order: once an order has moved past
     * allocation — confirmed, shipped, cancelled — a late or replayed allocation
     * event must not drag it backwards.
     */
    public void applyAllocation(OrderStatus outcome, UUID reservationId, Instant expiresAt, String advice) {
        if (status != OrderStatus.PENDING_ALLOCATION) {
            return;
        }
        this.status = outcome;
        this.reservationId = reservationId;
        this.reservationExpiresAt = expiresAt;
        this.substitutionAdvice = advice;
        touch();
    }

    public void allocateLine(String sku, int qtyAllocated) {
        lines.stream()
                .filter(line -> line.getSku().equals(sku))
                .findFirst()
                .ifPresent(line -> line.allocate(qtyAllocated));
    }

    public void confirm() {
        if (!status.isConfirmable()) {
            throw new IllegalStateException(
                    "order %s cannot be confirmed from %s".formatted(orderId, status));
        }
        status = OrderStatus.CONFIRMED;
        touch();
    }

    public void cancel(String reason) {
        if (!status.isCancellable()) {
            throw new IllegalStateException(
                    "order %s cannot be cancelled from %s".formatted(orderId, status));
        }
        status = OrderStatus.CANCELLED;
        statusReason = reason;
        touch();
    }

    public void markShipped() {
        if (status == OrderStatus.SHIPPED) {
            return;
        }
        status = OrderStatus.SHIPPED;
        touch();
    }

    public boolean isOwnedBy(UUID candidate) {
        return customerId.equals(candidate);
    }

    public BigDecimal total() {
        return lines.stream()
                .map(OrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void touch() {
        updatedAt = Instant.now();
    }
}
