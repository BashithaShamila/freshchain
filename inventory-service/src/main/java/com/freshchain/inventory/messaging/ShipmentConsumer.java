package com.freshchain.inventory.messaging;

import com.freshchain.events.EventEnvelope;
import com.freshchain.events.Topics;
import com.freshchain.inventory.service.ReservationService;
import com.freshchain.inventory.support.IdempotencyGuard;
import com.freshchain.inventory.support.OrderMdc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Stock has left the building: on-hand finally drops. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentConsumer {

    static final String GROUP = "inventory.shipment-shipped";

    private final EventReader reader;
    private final IdempotencyGuard idempotency;
    private final ReservationService reservations;

    @KafkaListener(topics = Topics.FULFILLMENT_SHIPPED, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onShipmentShipped(String message) {
        EventEnvelope<EventEnvelope.ShipmentShipped> event =
                reader.read(message, EventEnvelope.ShipmentShipped.class);
        EventEnvelope.ShipmentShipped shipment = event.payload();

        OrderMdc.run(shipment.orderId(), () -> {
            if (!idempotency.claim(event.eventId(), GROUP)) {
                return;
            }
            reservations.consume(shipment.orderId(), shipment.lines());
        });
    }
}
