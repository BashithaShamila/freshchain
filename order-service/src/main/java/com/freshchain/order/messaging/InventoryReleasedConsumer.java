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

/**
 * The saga closing in the unhappy direction: stock went back, so the order
 * cannot stand. This is what catches the reservation that timed out while the
 * customer was still deciding.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryReleasedConsumer {

    static final String GROUP = "order.inventory-released";

    private final EventReader reader;
    private final IdempotencyGuard idempotency;
    private final OrderService orders;

    @KafkaListener(topics = Topics.INVENTORY_RELEASED, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onInventoryReleased(String message) {
        EventEnvelope<EventEnvelope.InventoryReleased> event =
                reader.read(message, EventEnvelope.InventoryReleased.class);

        OrderMdc.run(event.payload().orderId(), () -> {
            if (!idempotency.claim(event.eventId(), GROUP)) {
                return;
            }
            orders.applyRelease(event.payload());
        });
    }
}
