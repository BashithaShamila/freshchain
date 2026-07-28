package com.freshchain.inventory.domain;

/** Thrown when a write would break the reserved <= on-hand invariant. */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
