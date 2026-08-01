package com.freshchain.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freshchain.events.AllocationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure algorithm tests: no Spring context, no database, no clock. The whole
 * suite runs in single-digit milliseconds, which is the point of keeping the
 * allocator free of infrastructure.
 */
class FefoAllocatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 22);
    private static final String SKU = "CHK-BRST-5LB";

    private final FefoAllocator allocator = new FefoAllocator();

    @Nested
    @DisplayName("expiry ordering")
    class ExpiryOrdering {

        @Test
        void drawsFromTheNearestExpiryLotFirst() {
            LotSnapshot expiresSoon = lot("2026-08-24", 25, 0);
            LotSnapshot expiresLater = lot("2026-09-02", 100, 0);

            AllocationResult result = allocator.allocate(
                    List.of(expiresLater, expiresSoon), 10, false, TODAY);

            assertThat(result.picks()).singleElement()
                    .satisfies(pick -> {
                        assertThat(pick.lotId()).isEqualTo(expiresSoon.lotId());
                        assertThat(pick.qty()).isEqualTo(10);
                    });
        }

        @Test
        void spillsOntoTheNextLotOnlyWhenTheNearestIsExhausted() {
            LotSnapshot expiresSoon = lot("2026-08-24", 25, 0);
            LotSnapshot expiresLater = lot("2026-09-02", 100, 0);

            AllocationResult result = allocator.allocate(
                    List.of(expiresLater, expiresSoon), 40, false, TODAY);

            assertThat(result.status()).isEqualTo(AllocationStatus.FULL);
            assertThat(result.picks()).extracting(LotPick::lotId, LotPick::qty)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(expiresSoon.lotId(), 25),
                            org.assertj.core.groups.Tuple.tuple(expiresLater.lotId(), 15));
        }

        @Test
        void breaksExpiryTiesOnTheOlderReceipt() {
            Instant monday = Instant.parse("2026-08-17T08:00:00Z");
            LotSnapshot receivedLater = lot(UUID.randomUUID(), "2026-08-30", monday.plus(2, ChronoUnit.DAYS), 10, 0);
            LotSnapshot receivedEarlier = lot(UUID.randomUUID(), "2026-08-30", monday, 10, 0);

            AllocationResult result = allocator.allocate(
                    List.of(receivedLater, receivedEarlier), 5, false, TODAY);

            assertThat(result.picks()).singleElement()
                    .extracting(LotPick::lotId).isEqualTo(receivedEarlier.lotId());
        }
    }

    @Nested
    @DisplayName("what counts as available")
    class Availability {

        @Test
        void ignoresLotsThatHaveAlreadyExpired() {
            AllocationResult result = allocator.allocate(
                    List.of(lot("2026-08-01", 500, 0)), 1, true, TODAY);

            assertThat(result).isInstanceOf(AllocationResult.Rejected.class);
            assertThat(((AllocationResult.Rejected) result).reason())
                    .isEqualTo("no unexpired stock available");
        }

        @Test
        void ignoresLotsExpiringToday() {
            // A case that goes out of date at midnight cannot be sold today.
            AllocationResult result = allocator.allocate(
                    List.of(lot("2026-08-22", 500, 0)), 1, true, TODAY);

            assertThat(result.status()).isEqualTo(AllocationStatus.REJECTED);
        }

        @Test
        void countsOnlyWhatIsNotAlreadyReserved() {
            AllocationResult result = allocator.allocate(
                    List.of(lot("2026-09-02", 100, 95)), 10, true, TODAY);

            assertThat(result.status()).isEqualTo(AllocationStatus.PARTIAL);
            assertThat(result.qtyAllocated()).isEqualTo(5);
            assertThat(result.shortfall()).isEqualTo(5);
        }

        @Test
        void skipsFullyReservedLotsEntirely() {
            LotSnapshot exhausted = lot("2026-08-24", 30, 30);
            LotSnapshot open = lot("2026-09-02", 30, 0);

            AllocationResult result = allocator.allocate(List.of(exhausted, open), 10, false, TODAY);

            assertThat(result.picks()).singleElement()
                    .extracting(LotPick::lotId).isEqualTo(open.lotId());
        }

        @Test
        void returnsRejectedWhenThereAreNoLotsAtAll() {
            assertThat(allocator.allocate(List.of(), 5, true, TODAY).status())
                    .isEqualTo(AllocationStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("partial fulfilment policy")
    class PartialFulfilment {

        @Test
        void allocatesTheExactRequestWhenStockMatchesPrecisely() {
            AllocationResult result = allocator.allocate(
                    List.of(lot("2026-09-02", 40, 0)), 40, false, TODAY);

            assertThat(result).isInstanceOf(AllocationResult.Full.class);
            assertThat(result.qtyAllocated()).isEqualTo(40);
            assertThat(result.shortfall()).isZero();
        }

        @Test
        void takesWhatItCanWhenTheOrderAllowsPartial() {
            AllocationResult result = allocator.allocate(
                    List.of(lot("2026-08-24", 25, 0), lot("2026-09-02", 5, 0)), 40, true, TODAY);

            assertThat(result).isInstanceOf(AllocationResult.Partial.class);
            assertThat(result.qtyAllocated()).isEqualTo(30);
            assertThat(result.shortfall()).isEqualTo(10);
            assertThat(result.picks()).hasSize(2);
        }

        @Test
        void allocatesNothingAtAllWhenTheOrderRefusesPartial() {
            // The alternative — handing over 30 of 40 cases anyway — leaves a
            // kitchen with an order it cannot cook. Better to allocate nothing
            // and let the buyer decide.
            AllocationResult result = allocator.allocate(
                    List.of(lot("2026-08-24", 25, 0), lot("2026-09-02", 5, 0)), 40, false, TODAY);

            assertThat(result).isInstanceOf(AllocationResult.Rejected.class);
            assertThat(result.picks()).isEmpty();
            assertThat(result.qtyAllocated()).isZero();
            assertThat(((AllocationResult.Rejected) result).reason())
                    .contains("short by 10")
                    .contains("partial fulfilment is not permitted");
        }
    }

    @Test
    void refusesToAllocateANonPositiveQuantity() {
        assertThatThrownBy(() -> allocator.allocate(List.of(lot("2026-09-02", 10, 0)), 0, true, TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("qtyRequested must be positive");
    }

    // ---------------------------------------------------------------- fixtures --

    private static LotSnapshot lot(String expiry, int onHand, int reserved) {
        return lot(UUID.randomUUID(), expiry, Instant.parse("2026-08-17T08:00:00Z"), onHand, reserved);
    }

    private static LotSnapshot lot(UUID id, String expiry, Instant receivedAt, int onHand, int reserved) {
        return new LotSnapshot(id, SKU, "WH-COL-01", LocalDate.parse(expiry), receivedAt, onHand, reserved);
    }
}
