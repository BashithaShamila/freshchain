package com.freshchain.fulfillment.domain;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A shipment against one order. Picking routes, wave planning and barcode
 * scanning are explicitly out of scope: this models the state transitions a
 * warehouse system would report, not the warehouse.
 */
@Entity
@Table(name = "shipment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

    @Id
    @Column(name = "shipment_id", nullable = false, updatable = false)
    private UUID shipmentId;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private String warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShipmentStatus status;

    @Column(name = "tracking_ref")
    private String trackingRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "picked_at")
    private Instant pickedAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ShipmentLine> lines = new ArrayList<>();

    public static Shipment create(UUID orderId, UUID customerId, String warehouseId) {
        Shipment shipment = new Shipment();
        Instant now = Instant.now();
        shipment.shipmentId = UUID.randomUUID();
        shipment.orderId = orderId;
        shipment.customerId = customerId;
        shipment.warehouseId = warehouseId;
        shipment.status = ShipmentStatus.CREATED;
        shipment.createdAt = now;
        shipment.updatedAt = now;
        return shipment;
    }

    public void addLine(UUID lotId, String sku, int qty) {
        lines.add(ShipmentLine.of(this, lotId, sku, qty));
    }

    public List<ShipmentLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public void pick() {
        requireStatus(ShipmentStatus.CREATED, "pick");
        status = ShipmentStatus.PICKED;
        pickedAt = Instant.now();
        touch();
    }

    /**
     * Shipping is the point of no return: it is what tells inventory to drop
     * on-hand, and there is no compensating event that puts a delivered pallet
     * back on the rack.
     */
    public void ship(String trackingRef) {
        requireStatus(ShipmentStatus.PICKED, "ship");
        status = ShipmentStatus.SHIPPED;
        this.trackingRef = trackingRef;
        shippedAt = Instant.now();
        touch();
    }

    public void cancel() {
        if (status == ShipmentStatus.SHIPPED) {
            throw new IllegalStateException(
                    "shipment %s has already shipped and cannot be cancelled".formatted(shipmentId));
        }
        status = ShipmentStatus.CANCELLED;
        touch();
    }

    public boolean isShipped() {
        return status == ShipmentStatus.SHIPPED;
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    private void requireStatus(ShipmentStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "cannot %s shipment %s: status is %s, expected %s"
                            .formatted(action, shipmentId, status, expected));
        }
    }
}
