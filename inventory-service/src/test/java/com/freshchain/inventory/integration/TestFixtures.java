package com.freshchain.inventory.integration;

import com.freshchain.events.Actor;
import com.freshchain.events.EventEnvelope;
import java.util.List;
import java.util.UUID;

/** Small builders so the tests read as scenarios rather than as construction. */
public final class TestFixtures {

    public static final String WAREHOUSE = "WH-COL-01";
    public static final Actor CUSTOMER = new Actor(UUID.randomUUID().toString(), List.of("CUSTOMER"));

    public static EventEnvelope.OrderPlaced order(UUID orderId, boolean allowPartial,
                                                  EventEnvelope.OrderPlaced.Line... lines) {
        return new EventEnvelope.OrderPlaced(
                orderId, UUID.randomUUID(), WAREHOUSE, allowPartial, List.of(lines));
    }

    public static EventEnvelope.OrderPlaced.Line line(String sku, int qty) {
        return new EventEnvelope.OrderPlaced.Line(sku, qty);
    }

    private TestFixtures() {
    }
}
