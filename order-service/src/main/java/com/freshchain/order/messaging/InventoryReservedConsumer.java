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

/** Turns the allocation outcome into an order status. */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryReservedConsumer {

    static final String GROUP = "order.inventory-reserved";

    private final EventReader reader;
    private final IdempotencyGuard idempotency;
    private final OrderService orders;

    @KafkaListener(topics = Topics.INVENTORY_RESERVED, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onInventoryReserved(String message) {
        EventEnvelope<EventEnvelope.InventoryReserved> event =
                reader.read(message, EventEnvelope.InventoryReserved.class);

        OrderMdc.run(event.payload().orderId(), () -> {
            if (!idempotency.claim(event.eventId(), GROUP)) {
                return;
            }
            orders.applyAllocation(event.payload());
        });
    }
}
