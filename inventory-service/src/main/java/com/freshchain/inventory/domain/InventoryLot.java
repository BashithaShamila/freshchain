package com.freshchain.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A physical receipt of one SKU with one expiry date. Inventory is not fungible:
 * "500 units of chicken breast" does not exist, only lot A expiring Tuesday and
 * lot B expiring next month.
 *
 * <p>Available quantity is derived, never stored. A third stored column would be
 * a third value free to drift out of step with the other two.
 */
@Entity
@Table(name = "inventory_lot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryLot {

    @Id
    @Column(name = "lot_id", nullable = false, updatable = false)
    private UUID lotId;

    @Column(name = "sku", nullable = false, updatable = false)
    private String sku;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private String warehouseId;

    @Column(name = "qty_on_hand", nullable = false)
    private int qtyOnHand;

    @Column(name = "qty_reserved", nullable = false)
    private int qtyReserved;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static InventoryLot receive(String sku, String warehouseId, int qty, LocalDate expiryDate) {
        if (qty <= 0) {
            throw new IllegalArgumentException("received quantity must be positive, got " + qty);
        }
        InventoryLot lot = new InventoryLot();
        lot.lotId = UUID.randomUUID();
        lot.sku = sku;
        lot.warehouseId = warehouseId;
        lot.qtyOnHand = qty;
        lot.qtyReserved = 0;
        lot.expiryDate = expiryDate;
        lot.receivedAt = Instant.now();
        return lot;
    }

    public int available() {
        return Math.max(0, qtyOnHand - qtyReserved);
    }

    /** Hold stock for an order. Caller must already hold the row lock. */
    public void reserve(int qty) {
        require(qty > 0, "reserve quantity must be positive, got " + qty);
        if (qty > available()) {
            throw new InsufficientStockException(
                    "lot %s has %d available but %d were requested".formatted(lotId, available(), qty));
        }
        qtyReserved += qty;
    }

    /** Give held stock back: cancellation, or a reservation that timed out. */
    public void release(int qty) {
        require(qty > 0, "release quantity must be positive, got " + qty);
        require(qty <= qtyReserved,
                "lot %s has %d reserved but %d were released".formatted(lotId, qtyReserved, qty));
        qtyReserved -= qty;
    }

    /** Stock physically left the building. Both counters drop together. */
    public void consume(int qty) {
        require(qty > 0, "consume quantity must be positive, got " + qty);
        require(qty <= qtyReserved,
                "lot %s has %d reserved but %d were shipped".formatted(lotId, qtyReserved, qty));
        require(qty <= qtyOnHand,
                "lot %s has %d on hand but %d were shipped".formatted(lotId, qtyOnHand, qty));
        qtyReserved -= qty;
        qtyOnHand -= qty;
    }

    /** Shrinkage, damage or a recount. Never allowed to push on-hand below reserved. */
    public void adjust(int delta) {
        int updated = qtyOnHand + delta;
        require(updated >= 0, "adjustment would take lot %s below zero on hand".formatted(lotId));
        require(updated >= qtyReserved,
                "adjustment would leave lot %s with %d on hand against %d already reserved"
                        .formatted(lotId, updated, qtyReserved));
        qtyOnHand = updated;
    }

    public boolean isExpiredOn(LocalDate today) {
        return !expiryDate.isAfter(today);
    }

    public LotSnapshot snapshot() {
        return new LotSnapshot(lotId, sku, warehouseId, expiryDate, receivedAt, qtyOnHand, qtyReserved);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InsufficientStockException(message);
        }
    }
}
