package com.freshchain.fulfillment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshchain.events.Actor;
import com.freshchain.events.AllocationStatus;
import com.freshchain.events.EventEnvelope;
import com.freshchain.events.EventTypes;
import com.freshchain.fulfillment.domain.Shipment;
import com.freshchain.fulfillment.domain.ShipmentStatus;
import com.freshchain.fulfillment.messaging.InventoryReservedConsumer;
import com.freshchain.fulfillment.messaging.OrderCancelledConsumer;
import com.freshchain.fulfillment.messaging.OrderConfirmedConsumer;
import com.freshchain.fulfillment.repository.ShipmentRepository;
import com.freshchain.fulfillment.service.FulfillmentService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A shipment needs two facts that travel on two topics. This proves it appears
 * regardless of which one lands first, and exactly once either way.
 */
class FulfillmentFlowIT extends AbstractIntegrationTest {

    private static final String SKU = "CHK-BRST-5LB";
    private static final UUID LOT = UUID.randomUUID();

    @Autowired
    private InventoryReservedConsumer reservedConsumer;

    @Autowired
    private OrderConfirmedConsumer confirmedConsumer;

    @Autowired
    private OrderCancelledConsumer cancelledConsumer;

    @Autowired
    private FulfillmentService fulfillment;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("allocation first, then confirmation, produces a shipment")
    void shipmentIsBuiltWhenAllocationArrivesFirst() throws Exception {
        UUID orderId = UUID.randomUUID();

        reservedConsumer.onInventoryReserved(reservedJson(orderId, 25));
        assertThat(shipments.findByOrderId(orderId)).as("not ready on the allocation alone").isEmpty();

        confirmedConsumer.onOrderConfirmed(confirmedJson(orderId));

        Shipment shipment = fulfillment.get(orderId);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CREATED);
        assertThat(shipment.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getSku()).isEqualTo(SKU);
            assertThat(line.getQty()).isEqualTo(25);
        });
    }

    @Test
    @DisplayName("confirmation first, then allocation, produces the same shipment")
    void shipmentIsBuiltWhenConfirmationArrivesFirst() throws Exception {
        UUID orderId = UUID.randomUUID();

        confirmedConsumer.onOrderConfirmed(confirmedJson(orderId));
        assertThat(shipments.findByOrderId(orderId)).as("not ready on the confirmation alone").isEmpty();

        reservedConsumer.onInventoryReserved(reservedJson(orderId, 25));

        assertThat(fulfillment.get(orderId).getStatus()).isEqualTo(ShipmentStatus.CREATED);
    }

    @Test
    @DisplayName("a rejected allocation never becomes a shipment")
    void rejectedAllocationsAreNotShipped() throws Exception {
        UUID orderId = UUID.randomUUID();

        reservedConsumer.onInventoryReserved(rejectedJson(orderId));
        confirmedConsumer.onOrderConfirmed(confirmedJson(orderId));

        assertThat(shipments.findByOrderId(orderId)).isEmpty();
    }

    @Test
    @DisplayName("picking then shipping announces the event that decrements stock")
    void shippingWritesTheOutboxEvent() throws Exception {
        UUID orderId = UUID.randomUUID();
        reservedConsumer.onInventoryReserved(reservedJson(orderId, 25));
        confirmedConsumer.onOrderConfirmed(confirmedJson(orderId));

        fulfillment.pick(orderId);
        Shipment shipped = fulfillment.ship(orderId, Actor.SYSTEM);

        assertThat(shipped.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        assertThat(shipped.getTrackingRef()).startsWith("FC-");

        String payload = jdbc.queryForObject("""
                SELECT payload::text FROM outbox WHERE aggregate_id = ? AND event_type = ?
                """, String.class, orderId, EventTypes.SHIPMENT_SHIPPED);
        JsonNode envelope = objectMapper.readTree(payload);
        assertThat(envelope.get("payload").get("lines").get(0).get("qty").asInt()).isEqualTo(25);

        // And it actually reaches the broker.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(
                jdbc.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NULL", Integer.class))
                .isZero());
    }

    @Test
    @DisplayName("a cancelled order takes its unshipped shipment with it")
    void cancellationCancelsTheShipment() throws Exception {
        UUID orderId = UUID.randomUUID();
        reservedConsumer.onInventoryReserved(reservedJson(orderId, 25));
        confirmedConsumer.onOrderConfirmed(confirmedJson(orderId));

        cancelledConsumer.onOrderCancelled(cancelledJson(orderId));

        assertThat(fulfillment.get(orderId).getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("a redelivered allocation does not create a second shipment")
    void replayedEventsDoNotDuplicateTheShipment() throws Exception {
        UUID orderId = UUID.randomUUID();
        String reserved = reservedJson(orderId, 25);

        reservedConsumer.onInventoryReserved(reserved);
        reservedConsumer.onInventoryReserved(reserved);
        confirmedConsumer.onOrderConfirmed(confirmedJson(orderId));

        assertThat(shipments.count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- fixtures --

    private String reservedJson(UUID orderId, int qty) throws Exception {
        return envelope(orderId, EventTypes.INVENTORY_RESERVED, new EventEnvelope.InventoryReserved(
                orderId, UUID.randomUUID(), "WH-COL-01", UUID.randomUUID(),
                AllocationStatus.FULL, Instant.now().plusSeconds(900),
                List.of(new EventEnvelope.InventoryReserved.ReservedLine(SKU, qty, qty,
                        List.of(new EventEnvelope.InventoryReserved.LotAllocation(LOT, qty, "2026-09-02")))),
                List.of()));
    }

    private String rejectedJson(UUID orderId) throws Exception {
        return envelope(orderId, EventTypes.INVENTORY_RESERVED, new EventEnvelope.InventoryReserved(
                orderId, UUID.randomUUID(), "WH-COL-01", null,
                AllocationStatus.REJECTED, null,
                List.of(new EventEnvelope.InventoryReserved.ReservedLine(SKU, 40, 0, List.of())),
                List.of()));
    }

    private String confirmedJson(UUID orderId) throws Exception {
        return envelope(orderId, EventTypes.ORDER_CONFIRMED,
                new EventEnvelope.OrderConfirmed(orderId, UUID.randomUUID()));
    }

    private String cancelledJson(UUID orderId) throws Exception {
        return envelope(orderId, EventTypes.ORDER_CANCELLED,
                new EventEnvelope.OrderCancelled(orderId, UUID.randomUUID(), "customer changed their mind"));
    }

    private <T> String envelope(UUID aggregateId, String eventType, T payload) throws Exception {
        return objectMapper.writeValueAsString(
                EventEnvelope.of(eventType, aggregateId, null, Actor.SYSTEM, payload));
    }
}
