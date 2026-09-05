package com.freshchain.fulfillment.messaging;

import com.freshchain.events.EventEnvelope;
import com.freshchain.events.Topics;
import com.freshchain.fulfillment.service.FulfillmentService;
import com.freshchain.fulfillment.support.IdempotencyGuard;
import com.freshchain.fulfillment.support.OrderMdc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancelledConsumer {

    static final String GROUP = "fulfillment.order-cancelled";

    private final EventReader reader;
    private final IdempotencyGuard idempotency;
    private final FulfillmentService fulfillment;

    @KafkaListener(topics = Topics.ORDERS_CANCELLED, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onOrderCancelled(String message) {
        EventEnvelope<EventEnvelope.OrderCancelled> event =
                reader.read(message, EventEnvelope.OrderCancelled.class);

        OrderMdc.run(event.payload().orderId(), () -> {
            if (!idempotency.claim(event.eventId(), GROUP)) {
                return;
            }
            fulfillment.cancel(event.payload().orderId());
        });
    }
}
