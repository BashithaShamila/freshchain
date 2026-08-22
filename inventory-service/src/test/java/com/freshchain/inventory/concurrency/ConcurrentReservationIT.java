package com.freshchain.inventory.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.freshchain.events.AllocationStatus;
import com.freshchain.inventory.domain.InventoryLot;
import com.freshchain.inventory.integration.AbstractIntegrationTest;
import com.freshchain.inventory.integration.TestFixtures;
import com.freshchain.inventory.repository.InventoryLotRepository;
import com.freshchain.inventory.repository.ReservationRepository;
import com.freshchain.inventory.service.ReservationService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The headline test.
 *
 * <p>Fifty threads race for ten units. The only acceptable outcome is that ten
 * of them win, forty are told no, and the lot's reserved count lands on exactly
 * ten. Anything else is overselling perishable stock a warehouse does not have.
 *
 * <p>What makes it pass is pessimistic row locking in
 * {@code InventoryLotRepository.lockAllocatableLots}: a blocking
 * {@code SELECT ... FOR UPDATE} that serialises the read-decide-write cycle,
 * with Postgres re-checking the {@code qty_on_hand > qty_reserved} predicate
 * after each waiter is granted its lock, so an exhausted lot drops out of the
 * result instead of being handed out twice. The database check constraint
 * {@code chk_reserved_le_onhand} stands behind that as a second line of defence.
 */
class ConcurrentReservationIT extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentReservationIT.class);

    private static final String SKU = "CHK-BRST-5LB";
    private static final int UNITS_ON_HAND = 10;
    private static final int CONTENDERS = 50;

    @Autowired
    private ReservationService reservations;

    @Autowired
    private InventoryLotRepository lots;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("50 concurrent reservations against 10 units allocate exactly 10")
    void fiftyConcurrentReservationsOnTenUnitsAllocateExactlyTen() throws Exception {
        // given: one lot, ten units on hand, nothing reserved
        InventoryLot lot = lots.save(
                InventoryLot.receive(SKU, TestFixtures.WAREHOUSE, UNITS_ON_HAND, LocalDate.now().plusDays(7)));

        // when: fifty threads each ask for a single unit, released together
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();

        List<AllocationStatus> outcomes;
        try (ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS)) {
            List<Future<AllocationStatus>> futures = IntStream.range(0, CONTENDERS)
                    .mapToObj(i -> pool.submit((Callable<AllocationStatus>) () -> {
                        startGun.await();
                        try {
                            return reservations.reserve(
                                    TestFixtures.order(UUID.randomUUID(), false, TestFixtures.line(SKU, 1)),
                                    TestFixtures.CUSTOMER);
                        } catch (RuntimeException e) {
                            // A lock timeout or a constraint violation would land
                            // here. Neither should ever happen, so count it.
                            failures.incrementAndGet();
                            log.error("reservation threw", e);
                            return AllocationStatus.REJECTED;
                        }
                    }))
                    .toList();

            startGun.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS))
                    .as("all fifty attempts finished within the timeout")
                    .isTrue();

            outcomes = futures.stream().map(ConcurrentReservationIT::get).toList();
        }

        Map<AllocationStatus, Long> tally = outcomes.stream()
                .collect(java.util.stream.Collectors.groupingBy(s -> s, java.util.stream.Collectors.counting()));
        log.info("outcome of {} concurrent single-unit reservations against {} units: {}",
                CONTENDERS, UNITS_ON_HAND, tally);

        // then: exactly ten succeeded and forty were refused
        assertThat(failures.get()).as("no attempt failed with an exception").isZero();
        assertThat(tally.getOrDefault(AllocationStatus.FULL, 0L))
                .as("exactly %d reservations succeed", UNITS_ON_HAND)
                .isEqualTo(UNITS_ON_HAND);
        assertThat(tally.getOrDefault(AllocationStatus.REJECTED, 0L))
                .as("the remaining %d are rejected", CONTENDERS - UNITS_ON_HAND)
                .isEqualTo(CONTENDERS - UNITS_ON_HAND);
        assertThat(tally.getOrDefault(AllocationStatus.PARTIAL, 0L))
                .as("a single-unit line is never partially filled")
                .isZero();

        // and: the lot itself agrees
        InventoryLot after = lots.findById(lot.getLotId()).orElseThrow();
        assertThat(after.getQtyReserved()).as("lot.qty_reserved").isEqualTo(UNITS_ON_HAND);
        assertThat(after.getQtyOnHand()).as("nothing has shipped yet, so on-hand is untouched")
                .isEqualTo(UNITS_ON_HAND);
        assertThat(after.available()).isZero();

        // and: so do the reservation lines, read straight from the database
        Integer reservedAcrossLines = jdbc.queryForObject("""
                SELECT COALESCE(SUM(rl.qty), 0) FROM reservation_line rl WHERE rl.lot_id = ?
                """, Integer.class, lot.getLotId());
        assertThat(reservedAcrossLines).as("sum(reservation_line.qty)").isEqualTo(UNITS_ON_HAND);
        assertThat(reservationRepository.count()).isEqualTo(UNITS_ON_HAND);
    }

    @Test
    @DisplayName("the database refuses an oversell even if the application logic is bypassed")
    void checkConstraintRefusesReservedExceedingOnHand() {
        InventoryLot lot = lots.save(
                InventoryLot.receive(SKU, TestFixtures.WAREHOUSE, 5, LocalDate.now().plusDays(7)));

        // Deliberately going around the entity and the service, straight at the
        // table, to prove the last line of defence is real and not decorative.
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                jdbc.update("UPDATE inventory_lot SET qty_reserved = qty_on_hand + 1 WHERE lot_id = ?",
                        lot.getLotId())))
                .as("chk_reserved_le_onhand rejects the write")
                .isNotNull()
                .hasMessageContaining("chk_reserved_le_onhand");
    }

    private static AllocationStatus get(Future<AllocationStatus> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
