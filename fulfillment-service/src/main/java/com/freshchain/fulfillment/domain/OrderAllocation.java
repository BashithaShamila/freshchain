package com.freshchain.fulfillment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * What this service knows about an order so far.
 *
 * <p>Two independent facts have to meet before a shipment can exist: inventory
 * allocated the stock, and the customer confirmed the order. They arrive on two
 * topics, and Kafka only orders records within a partition of a single topic —
 * so either can land first. Rather than assume a sequence, both are recorded
 * here and the shipment is created by whichever event completes the pair.
 */
@Entity
@Table(name = "order_allocation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderAllocation {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "warehouse_id")
    private String warehouseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lines")
    private String lines;

    @Column(name = "allocated", nullable = false)
    private boolean allocated;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

    @Column(name = "shipment_created", nullable = false)
    private boolean shipmentCreated;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static OrderAllocation forOrder(UUID orderId) {
        OrderAllocation allocation = new OrderAllocation();
        allocation.orderId = orderId;
        allocation.updatedAt = Instant.now();
        return allocation;
    }

    public void recordAllocation(UUID reservationId, UUID customerId, String warehouseId, String linesJson) {
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.warehouseId = warehouseId;
        this.lines = linesJson;
        this.allocated = true;
        touch();
    }

    public void recordConfirmation(UUID customerId) {
        if (this.customerId == null) {
            this.customerId = customerId;
        }
        this.confirmed = true;
        touch();
    }

    public void markShipmentCreated() {
        this.shipmentCreated = true;
        touch();
    }

    /** Both halves present, and nothing built yet. */
    public boolean isReadyToShip() {
        return allocated && confirmed && !shipmentCreated && lines != null;
    }

    private void touch() {
        updatedAt = Instant.now();
    }
}
