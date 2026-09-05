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

/**
 * Half of what is needed to build a shipment: which lots were set aside. The
 * other half is the confirmation, which may arrive before or after this.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryReservedConsumer {

    static final String GROUP = "fulfillment.inventory-reserved";

    private final EventReader reader;
    private final IdempotencyGuard idempotency;
    private final FulfillmentService fulfillment;

    @KafkaListener(topics = Topics.INVENTORY_RESERVED, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onInventoryReserved(String message) {
        EventEnvelope<EventEnvelope.InventoryReserved> event =
                reader.read(message, EventEnvelope.InventoryReserved.class);

        OrderMdc.run(event.payload().orderId(), () -> {
            if (!idempotency.claim(event.eventId(), GROUP)) {
                return;
            }
            fulfillment.recordAllocation(event.payload());
        });
    }
}
