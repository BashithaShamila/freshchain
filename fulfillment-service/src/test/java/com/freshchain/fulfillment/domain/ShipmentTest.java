package com.freshchain.fulfillment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShipmentTest {

    @Test
    void aNewShipmentIsWaitingToBePicked() {
        assertThat(shipment().getStatus()).isEqualTo(ShipmentStatus.CREATED);
    }

    @Test
    @DisplayName("a shipment cannot be dispatched before it has been picked")
    void cannotShipBeforePicking() {
        assertThatThrownBy(() -> shipment().ship("FC-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status is CREATED, expected PICKED");
    }

    @Test
    void pickingThenShippingRecordsBothTimestamps() {
        Shipment shipment = shipment();

        shipment.pick();
        shipment.ship("FC-ABCD1234");

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(shipment.getPickedAt()).isNotNull();
        assertThat(shipment.getShippedAt()).isNotNull();
        assertThat(shipment.getTrackingRef()).isEqualTo("FC-ABCD1234");
    }

    @Test
    void pickingTwiceIsRefusedRatherThanSilentlyIgnored() {
        Shipment shipment = shipment();
        shipment.pick();

        assertThatThrownBy(shipment::pick).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("dispatch is the point of no return: nothing puts a delivered pallet back")
    void aShippedShipmentCannotBeCancelled() {
        Shipment shipment = shipment();
        shipment.pick();
        shipment.ship("FC-ABCD1234");

        assertThatThrownBy(shipment::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already shipped");
    }

    @Test
    void anUnpickedShipmentCanStillBeCancelled() {
        Shipment shipment = shipment();

        shipment.cancel();

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
    }

    private static Shipment shipment() {
        Shipment shipment = Shipment.create(UUID.randomUUID(), UUID.randomUUID(), "WH-COL-01");
        shipment.addLine(UUID.randomUUID(), "CHK-BRST-5LB", 25);
        return shipment;
    }
}
