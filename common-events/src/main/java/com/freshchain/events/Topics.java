package com.freshchain.events;

/**
 * Every topic is keyed on {@code orderId}, which is what buys the saga its
 * ordering guarantee: all events for one order land on one partition and are
 * therefore consumed in the order they were produced.
 *
 * <p>The alternative — keying inventory events on {@code sku} — would serialise
 * allocation per product and remove the need for row locks entirely, at the
 * cost of hot partitions on popular SKUs. See docs/adr/ADR-001.
 */
public final class Topics {

    public static final String ORDERS_PLACED       = "orders.placed";
    public static final String ORDERS_CONFIRMED    = "orders.confirmed";
    public static final String ORDERS_CANCELLED    = "orders.cancelled";
    public static final String INVENTORY_RESERVED  = "inventory.reserved";
    public static final String INVENTORY_RELEASED  = "inventory.released";
    public static final String FULFILLMENT_SHIPPED = "fulfillment.shipped";

    /** Spring Kafka's DeadLetterPublishingRecoverer appends this to the source topic. */
    public static final String DLT_SUFFIX = ".DLT";

    public static String deadLetterFor(String topic) {
        return topic + DLT_SUFFIX;
    }

    private Topics() {
    }
}
