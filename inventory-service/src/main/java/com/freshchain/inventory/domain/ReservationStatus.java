package com.freshchain.inventory.domain;

/**
 * HELD -> CONFIRMED -> CONSUMED is the happy path. RELEASED is reachable from
 * HELD or CONFIRMED, but not from CONSUMED. Note that the expiry sweeper only
 * touches HELD: once an order is confirmed the hold no longer times out.
 */
public enum ReservationStatus {
    HELD,
    CONFIRMED,
    RELEASED,
    CONSUMED;

    public boolean isTerminal() {
        return this == RELEASED || this == CONSUMED;
    }
}
