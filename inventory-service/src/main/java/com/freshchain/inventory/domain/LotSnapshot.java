package com.freshchain.inventory.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An immutable read of a lot at one instant, which is all the allocator needs.
 * Keeping this separate from the {@link InventoryLot} entity is what lets the
 * allocation algorithm be unit-tested in milliseconds with no database.
 */
public record LotSnapshot(
        UUID lotId,
        String sku,
        String warehouseId,
        LocalDate expiryDate,
        Instant receivedAt,
        int qtyOnHand,
        int qtyReserved) {

    public int available() {
        return Math.max(0, qtyOnHand - qtyReserved);
    }

    /** A lot expiring today is already unsellable; the cutoff is strictly after today. */
    public boolean isUsableOn(LocalDate today) {
        return expiryDate.isAfter(today);
    }

    /** Returns a copy with {@code qty} more units reserved, for in-transaction bookkeeping. */
    public LotSnapshot withAdditionalReserved(int qty) {
        return new LotSnapshot(lotId, sku, warehouseId, expiryDate, receivedAt, qtyOnHand, qtyReserved + qty);
    }
}
