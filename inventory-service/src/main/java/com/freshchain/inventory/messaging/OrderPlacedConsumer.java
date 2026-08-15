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

/** Allocates stock when an order is placed. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedConsumer {

    static final String GROUP = "inventory.order-placed";

    private final EventReader reader;
    private final IdempotencyGuard idempotency;
    private final ReservationService reservations;

    /**
     * The whole handler is one transaction: the idempotency claim, the row locks
     * the allocator takes, the reservation, and the outbox row announcing the
     * outcome all commit together or not at all.
     */
    @KafkaListener(topics = Topics.ORDERS_PLACED, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onOrderPlaced(String message) {
        EventEnvelope<EventEnvelope.OrderPlaced> event =
                reader.read(message, EventEnvelope.OrderPlaced.class);
        EventEnvelope.OrderPlaced order = event.payload();

        OrderMdc.run(order.orderId(), () -> {
            if (!idempotency.claim(event.eventId(), GROUP)) {
                return;
            }
            log.info("allocating order {} ({} line(s), partial {})",
                    order.orderId(), order.lines().size(),
                    order.allowPartial() ? "allowed" : "not allowed");
            reservations.reserve(order, event.actor());
        });
    }
}
