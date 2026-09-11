package com.freshchain.inventory.chaos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.freshchain.events.AllocationStatus;
import com.freshchain.inventory.domain.InventoryLot;
import com.freshchain.inventory.integration.AbstractIntegrationTest;
import com.freshchain.inventory.integration.Containers;
import com.freshchain.inventory.integration.TestFixtures;
import com.freshchain.inventory.repository.InventoryLotRepository;
import com.freshchain.inventory.repository.ReservationRepository;
import com.freshchain.inventory.service.ReservationService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Chaos: what happens to the system when Kafka is simply not there.
 *
 * <p>This is the test that justifies the transactional outbox. With a
 * publish-inside-the-transaction design, a broker outage either loses the event
 * or fails the order. With the outbox, the order commits, the event waits
 * durably in Postgres, and the publisher drains the backlog when the broker
 * comes back — no operator involvement, no lost events.
 *
 * <p>The broker is paused rather than stopped, so its published port survives
 * and the application keeps pointing at the same address it started with. That
 * models an unreachable broker, which is the more common failure than one that
 * has been cleanly shut down.
 *
 * <p>Tagged {@code chaos} and excluded from the default build: it deliberately
 * spends time in a broken state. Run it with {@code make chaos}.
 */
@Tag("chaos")
class BrokerOutageIT extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BrokerOutageIT.class);
    private static final String SKU = "CHK-BRST-5LB";

    @Autowired
    private ReservationService reservations;

    @Autowired
    private InventoryLotRepository lots;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void ensureBrokerIsRunning() {
        if (isPaused()) {
            unpauseBroker();
        }
    }

    @Test
    @DisplayName("allocation keeps working while the broker is down, and events catch up afterwards")
    void ordersAreStillAcceptedWhileKafkaIsUnreachable() {
        InventoryLot lot = lots.saveAndFlush(
                InventoryLot.receive(SKU, TestFixtures.WAREHOUSE, 500, LocalDate.now().plusDays(10)));

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(unpublished()).isZero());

        pauseBroker();
        log.info("broker paused; placing orders into the outage");

        int ordersDuringOutage = 5;
        for (int i = 0; i < ordersDuringOutage; i++) {
            AllocationStatus status = reservations.reserve(
                    TestFixtures.order(UUID.randomUUID(), false, TestFixtures.line(SKU, 10)),
                    TestFixtures.CUSTOMER);

            // The database is the system of record and it is still up, so the
            // business operation succeeds even though nobody can be told yet.
            assertThat(status)
                    .as("allocation does not depend on the broker being reachable")
                    .isEqualTo(AllocationStatus.FULL);
        }

        assertThat(reservationRepository.count()).isEqualTo(ordersDuringOutage);
        assertThat(lots.findById(lot.getLotId()).orElseThrow().getQtyReserved())
                .isEqualTo(ordersDuringOutage * 10);

        // Events are queued durably rather than dropped.
        assertThat(unpublished())
                .as("every event written during the outage is still pending")
                .isEqualTo(ordersDuringOutage);
        log.info("{} event(s) queued in the outbox while the broker was unreachable", unpublished());

        unpauseBroker();
        log.info("broker resumed; waiting for the publisher to drain the backlog");

        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(unpublished())
                        .as("the backlog drains without any operator intervention")
                        .isZero());

        log.info("outbox fully drained after broker recovery");
    }

    // ---------------------------------------------------------------- helpers --

    private Integer unpublished() {
        return jdbc.queryForObject("SELECT count(*) FROM outbox WHERE published_at IS NULL", Integer.class);
    }

    private static boolean isPaused() {
        return Boolean.TRUE.equals(Containers.KAFKA.getDockerClient()
                .inspectContainerCmd(Containers.KAFKA.getContainerId())
                .exec().getState().getPaused());
    }

    private static void pauseBroker() {
        Containers.KAFKA.getDockerClient()
                .pauseContainerCmd(Containers.KAFKA.getContainerId()).exec();
    }

    private static void unpauseBroker() {
        Containers.KAFKA.getDockerClient()
                .unpauseContainerCmd(Containers.KAFKA.getContainerId()).exec();
    }
}
