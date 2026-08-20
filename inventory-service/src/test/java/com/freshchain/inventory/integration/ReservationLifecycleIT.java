package com.freshchain.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.freshchain.events.Actor;
import com.freshchain.events.AllocationStatus;
import com.freshchain.events.EventEnvelope;
import com.freshchain.inventory.domain.InventoryLot;
import com.freshchain.inventory.domain.Reservation;
import com.freshchain.inventory.domain.ReservationStatus;
import com.freshchain.inventory.repository.InventoryLotRepository;
import com.freshchain.inventory.repository.ReservationRepository;
import com.freshchain.inventory.service.ReservationService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The reservation state machine, exercised against a real database. */
class ReservationLifecycleIT extends AbstractIntegrationTest {

    private static final String SKU = "CHK-BRST-5LB";

    @Autowired
    private ReservationService reservations;

    @Autowired
    private InventoryLotRepository lots;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("a full allocation spans lots in expiry order, then confirms and consumes")
    void happyPathFromAllocationToConsumption() {
        InventoryLot expiresSoon = receive(25, 2);
        InventoryLot expiresLater = receive(100, 30);
        UUID orderId = UUID.randomUUID();

        AllocationStatus status = reservations.reserve(
                TestFixtures.order(orderId, false, TestFixtures.line(SKU, 40)), TestFixtures.CUSTOMER);

        assertThat(status).isEqualTo(AllocationStatus.FULL);

        Reservation reservation = reservationRepository.findByOrderId(orderId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.HELD);
        assertThat(reservation.getExpiresAt()).isAfter(java.time.Instant.now());

        // FEFO: the near-expiry lot is drained first, the remainder spills over.
        assertThat(lineQtyFor(expiresSoon)).isEqualTo(25);
        assertThat(lineQtyFor(expiresLater)).isEqualTo(15);
        assertThat(lots.findById(expiresSoon.getLotId()).orElseThrow().available()).isZero();
        assertThat(lots.findById(expiresLater.getLotId()).orElseThrow().available()).isEqualTo(85);

        reservations.confirm(orderId, TestFixtures.CUSTOMER);
        assertThat(reservationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);

        reservations.consume(orderId, List.of());

        // On-hand only moves when stock physically ships.
        assertThat(lots.findById(expiresSoon.getLotId()).orElseThrow().getQtyOnHand()).isZero();
        assertThat(lots.findById(expiresLater.getLotId()).orElseThrow().getQtyOnHand()).isEqualTo(85);
        assertThat(reservationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONSUMED);
    }

    @Test
    @DisplayName("cancelling mid-flight returns stock to exactly where it started")
    void cancellationRestoresAvailableStock() {
        InventoryLot lot = receive(50, 10);
        int availableBefore = lots.findById(lot.getLotId()).orElseThrow().available();
        UUID orderId = UUID.randomUUID();

        reservations.reserve(TestFixtures.order(orderId, false, TestFixtures.line(SKU, 30)),
                TestFixtures.CUSTOMER);
        assertThat(lots.findById(lot.getLotId()).orElseThrow().available()).isEqualTo(20);

        boolean released = reservations.release(orderId, "customer changed their mind", TestFixtures.CUSTOMER);

        assertThat(released).isTrue();
        assertThat(lots.findById(lot.getLotId()).orElseThrow().available()).isEqualTo(availableBefore);
        assertThat(lots.findById(lot.getLotId()).orElseThrow().getQtyReserved()).isZero();
        assertThat(reservationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    @DisplayName("releasing twice is a no-op rather than a double credit")
    void releaseIsIdempotent() {
        InventoryLot lot = receive(50, 10);
        UUID orderId = UUID.randomUUID();
        reservations.reserve(TestFixtures.order(orderId, false, TestFixtures.line(SKU, 30)),
                TestFixtures.CUSTOMER);

        assertThat(reservations.release(orderId, "first", TestFixtures.CUSTOMER)).isTrue();
        assertThat(reservations.release(orderId, "second", TestFixtures.CUSTOMER)).isFalse();

        assertThat(lots.findById(lot.getLotId()).orElseThrow().getQtyReserved()).isZero();
        assertThat(lots.findById(lot.getLotId()).orElseThrow().getQtyOnHand()).isEqualTo(50);
    }

    @Test
    @DisplayName("a partial allocation takes what it can and reports the shortfall")
    void partialAllocationWhenTheOrderAllowsIt() {
        receive(12, 5);
        UUID orderId = UUID.randomUUID();

        AllocationStatus status = reservations.reserve(
                TestFixtures.order(orderId, true, TestFixtures.line(SKU, 40)), TestFixtures.CUSTOMER);

        assertThat(status).isEqualTo(AllocationStatus.PARTIAL);
        // via the service, which initialises the lines inside its transaction
        assertThat(reservations.findByOrderId(orderId).orElseThrow().totalQty()).isEqualTo(12);
    }

    @Test
    @DisplayName("an order that refuses partial fulfilment holds nothing at all")
    void rejectedAllocationLeavesNoReservationAndNoHeldStock() {
        InventoryLot lot = receive(12, 5);
        UUID orderId = UUID.randomUUID();

        AllocationStatus status = reservations.reserve(
                TestFixtures.order(orderId, false, TestFixtures.line(SKU, 40)), TestFixtures.CUSTOMER);

        assertThat(status).isEqualTo(AllocationStatus.REJECTED);
        assertThat(reservationRepository.findByOrderId(orderId)).isEmpty();
        assertThat(lots.findById(lot.getLotId()).orElseThrow().getQtyReserved())
                .as("a rejected order must not leave stock stranded")
                .isZero();
    }

    @Test
    @DisplayName("two lines for the same SKU cannot both claim the same units")
    void repeatedSkuAcrossLinesSharesOneStockPool() {
        InventoryLot lot = receive(30, 6);
        UUID orderId = UUID.randomUUID();

        AllocationStatus status = reservations.reserve(
                TestFixtures.order(orderId, true, TestFixtures.line(SKU, 20), TestFixtures.line(SKU, 20)),
                TestFixtures.CUSTOMER);

        assertThat(status).isEqualTo(AllocationStatus.PARTIAL);
        assertThat(reservations.findByOrderId(orderId).orElseThrow().totalQty())
                .as("30 units exist, so 30 units are held — not 40")
                .isEqualTo(30);
        assertThat(lots.findById(lot.getLotId()).orElseThrow().getQtyReserved()).isEqualTo(30);
    }

    @Test
    @DisplayName("the sweeper releases a reservation nobody ever confirmed")
    void expiredReservationsAreSweptBackOntoTheShelf() {
        InventoryLot lot = receive(50, 10);
        UUID orderId = UUID.randomUUID();
        reservations.reserve(TestFixtures.order(orderId, false, TestFixtures.line(SKU, 30)),
                TestFixtures.CUSTOMER);
        assertThat(lots.findById(lot.getLotId()).orElseThrow().getQtyReserved()).isEqualTo(30);

        // Push the hold into the past rather than waiting fifteen real minutes.
        jdbc.update("UPDATE reservation SET expires_at = now() - interval '1 minute' WHERE order_id = ?",
                orderId);

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            assertThat(reservationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                    .isEqualTo(ReservationStatus.RELEASED);
            assertThat(lots.findById(lot.getLotId()).orElseThrow().getQtyReserved()).isZero();
        });
    }

    @Test
    @DisplayName("confirming after the sweep has run compensates instead of pretending")
    void confirmingAnExpiredReservationEmitsARelease() {
        receive(50, 10);
        UUID orderId = UUID.randomUUID();
        reservations.reserve(TestFixtures.order(orderId, false, TestFixtures.line(SKU, 30)),
                TestFixtures.CUSTOMER);
        reservations.release(orderId, "reservation expired", Actor.SYSTEM);

        reservations.confirm(orderId, TestFixtures.CUSTOMER);

        Integer releases = jdbc.queryForObject("""
                SELECT count(*) FROM outbox WHERE aggregate_id = ? AND event_type = 'InventoryReleased'
                """, Integer.class, orderId);
        assertThat(releases)
                .as("the release is announced again so the order service can cancel the order")
                .isEqualTo(2);
    }

    // ---------------------------------------------------------------- fixtures --

    private InventoryLot receive(int qty, int daysToExpiry) {
        return lots.saveAndFlush(InventoryLot.receive(
                SKU, TestFixtures.WAREHOUSE, qty, LocalDate.now().plusDays(daysToExpiry)));
    }

    private Integer lineQtyFor(InventoryLot lot) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(qty), 0) FROM reservation_line WHERE lot_id = ?",
                Integer.class, lot.getLotId());
    }
}
