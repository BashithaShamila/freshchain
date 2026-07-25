package com.freshchain.events;

public final class EventTypes {

    public static final String ORDER_PLACED       = "OrderPlaced";
    public static final String ORDER_CONFIRMED    = "OrderConfirmed";
    public static final String ORDER_CANCELLED    = "OrderCancelled";
    public static final String INVENTORY_RESERVED = "InventoryReserved";
    public static final String INVENTORY_RELEASED = "InventoryReleased";
    public static final String SHIPMENT_SHIPPED   = "ShipmentShipped";

    private EventTypes() {
    }
}
