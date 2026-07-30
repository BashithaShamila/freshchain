package com.freshchain.inventory.domain;

import java.time.LocalDate;
import java.util.UUID;

/** A decision to take {@code qty} units from one specific lot. */
public record LotPick(UUID lotId, String sku, int qty, LocalDate expiryDate) {

    public LotPick {
        if (qty <= 0) {
            throw new IllegalArgumentException("a pick must take at least one unit, got " + qty);
        }
    }
}
