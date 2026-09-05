package com.freshchain.fulfillment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Two facts arrive on two topics and Kafka orders records within a partition,
 * not across topics. Either can land first, so neither order may be assumed.
 */
class OrderAllocationTest {

    private static final String LINES = "[{\"lotId\":\"6f1c5e64-0000-0000-0000-000000000001\","
            + "\"sku\":\"CHK-BRST-5LB\",\"qty\":25}]";

    @Test
    @DisplayName("allocation alone is not enough to build a shipment")
    void allocationWithoutConfirmationIsNotReady() {
        OrderAllocation allocation = OrderAllocation.forOrder(UUID.randomUUID());
        allocation.recordAllocation(UUID.randomUUID(), UUID.randomUUID(), "WH-COL-01", LINES);

        assertThat(allocation.isReadyToShip()).isFalse();
    }

    @Test
    @DisplayName("confirmation alone is not enough either")
    void confirmationWithoutAllocationIsNotReady() {
        OrderAllocation allocation = OrderAllocation.forOrder(UUID.randomUUID());
        allocation.recordConfirmation(UUID.randomUUID());

        assertThat(allocation.isReadyToShip()).isFalse();
    }

    @Test
    void becomesReadyWhenTheAllocationArrivesFirst() {
        OrderAllocation allocation = OrderAllocation.forOrder(UUID.randomUUID());
        allocation.recordAllocation(UUID.randomUUID(), UUID.randomUUID(), "WH-COL-01", LINES);
        allocation.recordConfirmation(UUID.randomUUID());

        assertThat(allocation.isReadyToShip()).isTrue();
    }

    @Test
    void becomesReadyWhenTheConfirmationArrivesFirst() {
        OrderAllocation allocation = OrderAllocation.forOrder(UUID.randomUUID());
        allocation.recordConfirmation(UUID.randomUUID());
        allocation.recordAllocation(UUID.randomUUID(), UUID.randomUUID(), "WH-COL-01", LINES);

        assertThat(allocation.isReadyToShip()).isTrue();
    }

    @Test
    @DisplayName("once the shipment exists, a redelivery must not build a second one")
    void stopsBeingReadyOnceTheShipmentIsCreated() {
        OrderAllocation allocation = OrderAllocation.forOrder(UUID.randomUUID());
        allocation.recordAllocation(UUID.randomUUID(), UUID.randomUUID(), "WH-COL-01", LINES);
        allocation.recordConfirmation(UUID.randomUUID());

        allocation.markShipmentCreated();

        assertThat(allocation.isReadyToShip()).isFalse();
    }
}
