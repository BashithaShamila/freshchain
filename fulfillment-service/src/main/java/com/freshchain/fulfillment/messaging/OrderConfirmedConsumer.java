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

/** The other half: the customer has paid, so this order is worth picking. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderConfirmedConsumer {

    static final String GROUP = "fulfillment.order-confirmed";

    private final EventReader reader;
    private final IdempotencyGuard idempotency;
    private final FulfillmentService fulfillment;

    @KafkaListener(topics = Topics.ORDERS_CONFIRMED, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onOrderConfirmed(String message) {
        EventEnvelope<EventEnvelope.OrderConfirmed> event =
                reader.read(message, EventEnvelope.OrderConfirmed.class);

        OrderMdc.run(event.payload().orderId(), () -> {
            if (!idempotency.claim(event.eventId(), GROUP)) {
                return;
            }
            fulfillment.recordConfirmation(event.payload().orderId(), event.payload().customerId());
        });
    }
}
