package com.freshchain.inventory.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * First-Expired-First-Out allocation.
 *
 * <p>Pure by construction: no Spring, no JPA, no repository, no clock. It takes
 * a list of lot snapshots and a quantity, and returns a decision. Persisting
 * that decision — and holding the row locks that make it safe — is somebody
 * else's job. That separation is what lets the concurrency behaviour and the
 * allocation arithmetic be tested independently of each other.
 *
 * <p><b>Known limitation, stated deliberately:</b> greedy FEFO is locally
 * optimal and globally suboptimal. One large order can drain every near-expiry
 * lot that several small, soon-to-be-fulfilled orders could have absorbed,
 * pushing spoilage elsewhere in the network. A production system would weigh
 * remaining shelf life against the consuming order's own delivery date.
 */
public class FefoAllocator {

    /**
     * Nearest expiry first; oldest receipt breaks ties so the result is stable
     * and reproducible rather than dependent on row order.
     */
    public static final Comparator<LotSnapshot> FEFO_ORDER =
            Comparator.comparing(LotSnapshot::expiryDate)
                    .thenComparing(LotSnapshot::receivedAt)
                    .thenComparing(LotSnapshot::lotId);

    /**
     * @param lots         candidate lots, in any order; expired and empty lots are ignored
     * @param qtyRequested units the order line asked for
     * @param allowPartial whether the order accepts less than it asked for
     * @param today        allocation date, passed in so the result is deterministic under test
     */
    public AllocationResult allocate(List<LotSnapshot> lots, int qtyRequested, boolean allowPartial, LocalDate today) {
        if (qtyRequested <= 0) {
            throw new IllegalArgumentException("qtyRequested must be positive, got " + qtyRequested);
        }

        List<LotSnapshot> candidates = lots.stream()
                .filter(lot -> lot.isUsableOn(today))
                .filter(lot -> lot.available() > 0)
                .sorted(FEFO_ORDER)
                .toList();

        List<LotPick> picks = new ArrayList<>();
        int remaining = qtyRequested;

        for (LotSnapshot lot : candidates) {
            if (remaining == 0) {
                break;
            }
            int take = Math.min(lot.available(), remaining);
            picks.add(new LotPick(lot.lotId(), lot.sku(), take, lot.expiryDate()));
            remaining -= take;
        }

        if (remaining == 0) {
            return new AllocationResult.Full(qtyRequested, picks);
        }
        if (picks.isEmpty()) {
            return new AllocationResult.Rejected(qtyRequested, "no unexpired stock available");
        }
        if (allowPartial) {
            return new AllocationResult.Partial(qtyRequested, picks);
        }
        // Short, and the order will not take a partial: allocate nothing at all
        // rather than leaving the customer with an unusable fraction.
        return new AllocationResult.Rejected(
                qtyRequested,
                "short by " + remaining + " unit(s) and partial fulfilment is not permitted");
    }
}
