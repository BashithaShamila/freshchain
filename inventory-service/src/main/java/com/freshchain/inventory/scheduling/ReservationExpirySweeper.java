package com.freshchain.inventory.scheduling;

import com.freshchain.events.Actor;
import com.freshchain.inventory.config.ReservationProperties;
import com.freshchain.inventory.domain.Reservation;
import com.freshchain.inventory.repository.ReservationRepository;
import com.freshchain.inventory.service.ReservationService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives back stock that was held for an order nobody ever confirmed.
 *
 * <p>Without this, a customer who abandons a basket quietly removes inventory
 * from sale for good. It is the one part of the reservation lifecycle nothing
 * else can trigger, because the trigger is the absence of an event.
 *
 * <p>ShedLock, unlike on the outbox publisher, is worth it here: the sweep is
 * cheap and infrequent, and one replica doing it is enough.
 */
@Component
@Slf4j
public class ReservationExpirySweeper {

    private final ReservationRepository reservations;
    private final ReservationService reservationService;
    private final ReservationProperties properties;
    private final MeterRegistry meterRegistry;

    public ReservationExpirySweeper(ReservationRepository reservations,
                                    ReservationService reservationService,
                                    ReservationProperties properties,
                                    MeterRegistry meterRegistry) {
        this.reservations = reservations;
        this.reservationService = reservationService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${freshchain.reservation.sweep-interval-ms:10000}")
    @SchedulerLock(name = "reservationExpirySweeper", lockAtMostFor = "PT30S", lockAtLeastFor = "PT1S")
    @Transactional
    public void sweep() {
        Instant now = Instant.now();
        List<Reservation> expired =
                reservations.lockExpiredHeldReservations(now, properties.sweepBatchSize());
        if (expired.isEmpty()) {
            return;
        }

        for (Reservation reservation : expired) {
            try {
                reservationService.releaseExpired(reservation, Actor.SYSTEM);
            } catch (RuntimeException e) {
                // One bad reservation must not stop the sweep. The row stays HELD
                // and the next pass will try it again.
                log.error("could not release expired reservation {}", reservation.getReservationId(), e);
            }
        }

        meterRegistry.counter("freshchain.reservation.expired").increment(expired.size());
        log.info("released {} expired reservation(s)", expired.size());
    }
}
