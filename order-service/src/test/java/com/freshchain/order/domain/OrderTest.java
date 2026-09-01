package com.freshchain.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The order state machine, with no infrastructure anywhere near it. */
class OrderTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final String SKU = "CHK-BRST-5LB";

    @Test
    void anOrderStartsUnallocatedBecauseAllocationIsAsynchronous() {
        assertThat(order().getStatus()).isEqualTo(OrderStatus.PENDING_ALLOCATION);
    }

    @Test
    @DisplayName("a pending order cannot be confirmed before anyone knows if it can be filled")
    void cannotConfirmBeforeAllocation() {
        assertThatThrownBy(() -> order().confirm())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be confirmed from PENDING_ALLOCATION");
    }

    @Test
    void aPartiallyAllocatedOrderCanStillBeConfirmed() {
        Order order = order();
        order.applyAllocation(OrderStatus.PARTIALLY_ALLOCATED, UUID.randomUUID(), Instant.now(), null);

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void aRejectedOrderIsTerminalAndCannotBeConfirmed() {
        Order order = order();
        order.applyAllocation(OrderStatus.REJECTED, null, null, null);

        assertThatThrownBy(order::confirm).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> order.cancel("changed my mind")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a late allocation event cannot drag a confirmed order backwards")
    void allocationIsIgnoredOnceTheOrderHasMovedOn() {
        Order order = order();
        order.applyAllocation(OrderStatus.ALLOCATED, UUID.randomUUID(), Instant.now(), null);
        order.confirm();

        // A redelivery, or an event that overtook another, arriving after the fact.
        order.applyAllocation(OrderStatus.REJECTED, null, null, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void shippedOrdersCannotBeCancelled() {
        Order order = order();
        order.applyAllocation(OrderStatus.ALLOCATED, UUID.randomUUID(), Instant.now(), null);
        order.confirm();
        order.markShipped();

        assertThatThrownBy(() -> order.cancel("too late")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the total is priced on what was allocated, not on what was asked for")
    void totalReflectsAllocatedQuantity() {
        Order order = order();
        order.allocateLine(SKU, 15);

        assertThat(order.total()).isEqualByComparingTo(new BigDecimal("637.50"));
    }

    @Test
    void anUnallocatedOrderIsWorthNothing() {
        assertThat(order().total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void ownershipIsCheckedAgainstTheCustomerWhoPlacedIt() {
        Order order = order();

        assertThat(order.isOwnedBy(CUSTOMER)).isTrue();
        assertThat(order.isOwnedBy(UUID.randomUUID())).isFalse();
    }

    private static Order order() {
        Order order = Order.place(CUSTOMER, "WH-COL-01", false);
        order.addLine(SKU, 40, new BigDecimal("42.50"));
        return order;
    }
}
