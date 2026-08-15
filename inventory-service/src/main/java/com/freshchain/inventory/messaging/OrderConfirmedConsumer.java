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

/** Pins a reservation once payment has cleared, so expiry stops applying to it. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderConfirmedConsumer {

    static final String GROUP = "inventory.order-confirmed";

    private final EventReader reader;
    private final IdempotencyGuard idempotency;
    private final ReservationService reservations;

    @KafkaListener(topics = Topics.ORDERS_CONFIRMED, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onOrderConfirmed(String message) {
        EventEnvelope<EventEnvelope.OrderConfirmed> event =
                reader.read(message, EventEnvelope.OrderConfirmed.class);

        OrderMdc.run(event.payload().orderId(), () -> {
            if (!idempotency.claim(event.eventId(), GROUP)) {
                return;
            }
            reservations.confirm(event.payload().orderId(), event.actor());
        });
    }
}
