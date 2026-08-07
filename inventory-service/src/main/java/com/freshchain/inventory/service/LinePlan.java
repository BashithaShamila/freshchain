package com.freshchain.inventory.service;

import com.freshchain.inventory.domain.AllocationResult;

/** What the allocator decided for one order line, before anything is written. */
record LinePlan(String sku, int qtyRequested, AllocationResult result) {

    boolean isFull() {
        return result instanceof AllocationResult.Full;
    }

    int shortfall() {
        return result.shortfall();
    }
}
