package com.freshchain.order.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * <pre>
 * PENDING_ALLOCATION ──> ALLOCATED ───────────> CONFIRMED ──> SHIPPED
 *          │              PARTIALLY_ALLOCATED
 *          │                    │                    │
 *          └──> REJECTED        └──> CANCELLED <─────┘
 * </pre>
 *
 * Allocation is asynchronous, so an order exists in PENDING_ALLOCATION before
 * anyone knows whether it can be filled. That is why placement answers 202 and
 * not 201.
 */
public enum OrderStatus {

    PENDING_ALLOCATION,
    ALLOCATED,
    PARTIALLY_ALLOCATED,
    REJECTED,
    CONFIRMED,
    SHIPPED,
    CANCELLED;

    private static final Set<OrderStatus> CONFIRMABLE = EnumSet.of(ALLOCATED, PARTIALLY_ALLOCATED);
    private static final Set<OrderStatus> TERMINAL = EnumSet.of(REJECTED, SHIPPED, CANCELLED);

    public boolean isConfirmable() {
        return CONFIRMABLE.contains(this);
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** Anything not yet shipped or already dead can still be cancelled. */
    public boolean isCancellable() {
        return !isTerminal();
    }
}
