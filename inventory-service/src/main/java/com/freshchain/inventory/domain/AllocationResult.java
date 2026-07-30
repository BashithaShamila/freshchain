package com.freshchain.inventory.domain;

import com.freshchain.events.AllocationStatus;
import java.util.List;

/**
 * Outcome of allocating one order line. Sealed so that every call site is forced
 * to handle all three cases — a partial allocation is a first-class result here,
 * not an exception or a null.
 */
public sealed interface AllocationResult
        permits AllocationResult.Full, AllocationResult.Partial, AllocationResult.Rejected {

    List<LotPick> picks();

    int qtyRequested();

    AllocationStatus status();

    default int qtyAllocated() {
        return picks().stream().mapToInt(LotPick::qty).sum();
    }

    default int shortfall() {
        return qtyRequested() - qtyAllocated();
    }

    /** Every requested unit was allocated. */
    record Full(int qtyRequested, List<LotPick> picks) implements AllocationResult {
        public Full {
            picks = List.copyOf(picks);
        }

        @Override
        public AllocationStatus status() {
            return AllocationStatus.FULL;
        }
    }

    /** Some units allocated, some short. Only produced when the order allows it. */
    record Partial(int qtyRequested, List<LotPick> picks) implements AllocationResult {
        public Partial {
            picks = List.copyOf(picks);
        }

        @Override
        public AllocationStatus status() {
            return AllocationStatus.PARTIAL;
        }
    }

    /** Nothing allocated: no stock, or a shortfall the order refuses to accept. */
    record Rejected(int qtyRequested, String reason) implements AllocationResult {
        @Override
        public List<LotPick> picks() {
            return List.of();
        }

        @Override
        public AllocationStatus status() {
            return AllocationStatus.REJECTED;
        }
    }
}
