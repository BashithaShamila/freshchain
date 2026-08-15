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

/** The compensating side of the saga: a cancelled order hands its stock back. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancelledConsumer {

    static final String GROUP = "inventory.order-cancelled";

    private final EventReader reader;
    private final IdempotencyGuard idempotency;
    private final ReservationService reservations;

    @KafkaListener(topics = Topics.ORDERS_CANCELLED, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onOrderCancelled(String message) {
        EventEnvelope<EventEnvelope.OrderCancelled> event =
                reader.read(message, EventEnvelope.OrderCancelled.class);
        EventEnvelope.OrderCancelled cancellation = event.payload();

        OrderMdc.run(cancellation.orderId(), () -> {
            if (!idempotency.claim(event.eventId(), GROUP)) {
                return;
            }
            String reason = cancellation.reason() == null ? "order cancelled" : cancellation.reason();
            reservations.release(cancellation.orderId(), reason, event.actor());
        });
    }
}
