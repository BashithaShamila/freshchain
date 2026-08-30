package com.freshchain.order.messaging;

import com.freshchain.events.EventEnvelope;
import com.freshchain.events.Topics;
import com.freshchain.order.service.OrderService;
import com.freshchain.order.support.IdempotencyGuard;
import com.freshchain.order.support.OrderMdc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentConsumer {

    static final String GROUP = "order.shipment-shipped";

    private final EventReader reader;
    private final IdempotencyGuard idempotency;
    private final OrderService orders;

    @KafkaListener(topics = Topics.FULFILLMENT_SHIPPED, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onShipmentShipped(String message) {
        EventEnvelope<EventEnvelope.ShipmentShipped> event =
                reader.read(message, EventEnvelope.ShipmentShipped.class);

        OrderMdc.run(event.payload().orderId(), () -> {
            if (!idempotency.claim(event.eventId(), GROUP)) {
                return;
            }
            orders.applyShipment(event.payload().orderId());
        });
    }
}
