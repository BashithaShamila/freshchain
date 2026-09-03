package com.freshchain.fulfillment.domain;

/** CREATED -> PICKED -> SHIPPED, or CANCELLED from anywhere before shipping. */
public enum ShipmentStatus {
    CREATED,
    PICKED,
    SHIPPED,
    CANCELLED
}
