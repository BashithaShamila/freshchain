package com.freshchain.inventory.service;

import com.freshchain.events.Actor;
import com.freshchain.events.AllocationStatus;
import com.freshchain.events.EventEnvelope;
import com.freshchain.events.EventTypes;
import com.freshchain.events.Topics;
import com.freshchain.inventory.config.ReservationProperties;
import com.freshchain.inventory.domain.AllocationResult;
import com.freshchain.inventory.domain.FefoAllocator;
import com.freshchain.inventory.domain.InventoryLot;
import com.freshchain.inventory.domain.LotPick;
import com.freshchain.inventory.domain.LotSnapshot;
import com.freshchain.inventory.domain.Reservation;
import com.freshchain.inventory.domain.ReservationLine;
import com.freshchain.inventory.repository.InventoryLotRepository;
import com.freshchain.inventory.repository.ReservationRepository;
import com.freshchain.inventory.substitution.SubstitutionAdvisor;
import com.freshchain.inventory.substitution.SubstitutionCandidate;
import com.freshchain.inventory.support.OutboxWriter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reservation lifecycle. Every method here runs in one transaction, and every
 * one of them ends by writing an outbox row rather than calling Kafka, so the
 * state change and the announcement of it commit together.
 */
@Service
@Slf4j
public class ReservationService {

    private static final String AGGREGATE = "Reservation";

    private final InventoryLotRepository lots;
    private final ReservationRepository reservations;
    private final FefoAllocator allocator;
    private final SubstitutionAdvisor substitutionAdvisor;
    private final OutboxWriter outbox;
    private final ReservationProperties properties;
    private final Timer allocationTimer;
    private final Counter oversellPrevented;

    public ReservationService(InventoryLotRepository lots,
                              ReservationRepository reservations,
                              FefoAllocator allocator,
                              SubstitutionAdvisor substitutionAdvisor,
                              OutboxWriter outbox,
                              ReservationProperties properties,
                              MeterRegistry meterRegistry) {
        this.lots = lots;
        this.reservations = reservations;
        this.allocator = allocator;
        this.substitutionAdvisor = substitutionAdvisor;
        this.outbox = outbox;
        this.properties = properties;
        this.allocationTimer = Timer.builder("freshchain.allocation")
                .description("time spent allocating one order, lock acquisition included")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.oversellPrevented = Counter.builder("freshchain.allocation.rejected")
                .description("order lines that could not be filled from available stock")
                .register(meterRegistry);
    }

    // ------------------------------------------------------------- allocate --

    /**
     * Allocate stock for a placed order and announce the outcome.
     *
     * <p>Runs in two phases on purpose. The <b>plan</b> phase takes row locks and
     * works out what every line would draw from which lot, mutating nothing. The
     * <b>apply</b> phase writes, and only runs if the order as a whole is going
     * ahead. Without that split, an order that allows no partial fulfilment
     * would have to increment counters for its early lines and then unpick them
     * when a later line came up short.
     */
    @Transactional
    public AllocationStatus reserve(EventEnvelope.OrderPlaced order, Actor actor) {
        return allocationTimer.record(() -> doReserve(order, actor));
    }

    private AllocationStatus doReserve(EventEnvelope.OrderPlaced order, Actor actor) {
        // Second idempotency guard, behind the processed-event table: the unique
        // constraint on order_id means one order can only ever hold stock once.
        Optional<Reservation> existing = reservations.findByOrderId(order.orderId());
        if (existing.isPresent()) {
            log.info("order {} already has reservation {}, skipping allocation",
                    order.orderId(), existing.get().getReservationId());
            return AllocationStatus.FULL;
        }

        LocalDate today = LocalDate.now();
        Map<UUID, InventoryLot> lockedLots = new LinkedHashMap<>();
        // Tracks stock this transaction has already spoken for, so two lines that
        // ask for the same SKU do not both allocate the same units.
        Map<UUID, Integer> claimedInThisTransaction = new HashMap<>();
        List<LinePlan> plans = new ArrayList<>();

        for (EventEnvelope.OrderPlaced.Line line : order.lines()) {
            List<InventoryLot> candidates =
                    lots.lockAllocatableLots(line.sku(), order.warehouseId(), today);
            candidates.forEach(lot -> lockedLots.putIfAbsent(lot.getLotId(), lot));

            List<LotSnapshot> snapshots = candidates.stream()
                    .map(InventoryLot::snapshot)
                    .map(snapshot -> snapshot.withAdditionalReserved(
                            claimedInThisTransaction.getOrDefault(snapshot.lotId(), 0)))
                    .toList();

            AllocationResult result =
                    allocator.allocate(snapshots, line.qty(), order.allowPartial(), today);
            result.picks().forEach(pick ->
                    claimedInThisTransaction.merge(pick.lotId(), pick.qty(), Integer::sum));

            plans.add(new LinePlan(line.sku(), line.qty(), result));
        }

        AllocationStatus status = overallStatus(plans);

        Reservation reservation = null;
        if (status != AllocationStatus.REJECTED) {
            reservation = Reservation.hold(
                    order.orderId(), order.customerId(), order.warehouseId(), properties.ttl());
            for (LinePlan plan : plans) {
                for (LotPick pick : plan.result().picks()) {
                    // Throws if the invariant would break; the DB check constraint
                    // stands behind this as the last line of defence.
                    lockedLots.get(pick.lotId()).reserve(pick.qty());
                    reservation.addLine(pick);
                }
            }
            reservations.save(reservation);
            log.info("order {} allocated {} across {} lot(s), status {}",
                    order.orderId(), reservation.totalQty(), reservation.getLines().size(), status);
        } else {
            log.info("order {} rejected: no line could be filled under its partial-fulfilment policy",
                    order.orderId());
        }

        plans.stream().filter(plan -> plan.shortfall() > 0).forEach(plan -> oversellPrevented.increment());

        outbox.write(AGGREGATE, order.orderId(), EventTypes.INVENTORY_RESERVED,
                Topics.INVENTORY_RESERVED,
                buildReservedEvent(order, reservation, plans, status), actor);

        return status;
    }

    /**
     * The order-level verdict is the weakest line outcome. Note that when the
     * order forbids partial fulfilment the allocator never returns Partial in
     * the first place, so a short line has already become a rejected one.
     */
    private AllocationStatus overallStatus(List<LinePlan> plans) {
        boolean anythingAllocated = plans.stream().anyMatch(plan -> plan.result().qtyAllocated() > 0);
        if (!anythingAllocated) {
            return AllocationStatus.REJECTED;
        }
        boolean allFull = plans.stream().allMatch(LinePlan::isFull);
        return allFull ? AllocationStatus.FULL : AllocationStatus.PARTIAL;
    }

    private EventEnvelope.InventoryReserved buildReservedEvent(EventEnvelope.OrderPlaced order,
                                                               Reservation reservation,
                                                               List<LinePlan> plans,
                                                               AllocationStatus status) {
        List<EventEnvelope.InventoryReserved.ReservedLine> lines = plans.stream()
                .map(plan -> new EventEnvelope.InventoryReserved.ReservedLine(
                        plan.sku(),
                        plan.qtyRequested(),
                        plan.result().qtyAllocated(),
                        plan.result().picks().stream()
                                .map(pick -> new EventEnvelope.InventoryReserved.LotAllocation(
                                        pick.lotId(), pick.qty(), pick.expiryDate().toString()))
                                .toList()))
                .toList();

        List<EventEnvelope.InventoryReserved.Substitution> substitutions = plans.stream()
                .filter(plan -> plan.shortfall() > 0)
                .flatMap(plan -> substitutionAdvisor
                        .adviseFor(plan.sku(), order.warehouseId(), plan.shortfall()).stream())
                .map(ReservationService::toEventSubstitution)
                .toList();

        return new EventEnvelope.InventoryReserved(
                order.orderId(),
                order.customerId(),
                order.warehouseId(),
                reservation == null ? null : reservation.getReservationId(),
                status,
                reservation == null ? null : reservation.getExpiresAt(),
                lines,
                substitutions);
    }

    private static EventEnvelope.InventoryReserved.Substitution toEventSubstitution(SubstitutionCandidate c) {
        return new EventEnvelope.InventoryReserved.Substitution(
                c.forSku(), c.suggestedSku(), c.suggestedName(), c.availableQty(), c.similarity(), c.rationale());
    }

    // -------------------------------------------------------------- confirm --

    /**
     * Payment cleared. The reservation stops being subject to expiry.
     *
     * <p>The case worth thinking about is the one where the sweeper got there
     * first: the customer confirmed an order whose stock has already gone back
     * on the shelf. Rather than pretend otherwise, this re-announces the release
     * so the order service can compensate and cancel the order.
     */
    @Transactional
    public void confirm(UUID orderId, Actor actor) {
        Optional<Reservation> found = reservations.lockByOrderId(orderId);
        if (found.isEmpty()) {
            log.warn("confirm for order {} has no reservation; it was rejected at allocation", orderId);
            return;
        }
        Reservation reservation = found.get();

        switch (reservation.getStatus()) {
            case HELD -> {
                reservation.confirm();
                log.info("reservation {} confirmed for order {}", reservation.getReservationId(), orderId);
            }
            case CONFIRMED, CONSUMED ->
                    log.debug("reservation for order {} already {}", orderId, reservation.getStatus());
            case RELEASED -> {
                log.warn("order {} confirmed but its reservation had already expired; compensating", orderId);
                outbox.write(AGGREGATE, orderId, EventTypes.INVENTORY_RELEASED,
                        Topics.INVENTORY_RELEASED,
                        new EventEnvelope.InventoryReleased(orderId, reservation.getReservationId(),
                                "reservation expired before confirmation"),
                        actor);
            }
        }
    }

    // -------------------------------------------------------------- release --

    /** Compensation: give the stock back and say so. Safe to call twice. */
    @Transactional
    public boolean release(UUID orderId, String reason, Actor actor) {
        Optional<Reservation> found = reservations.lockByOrderId(orderId);
        if (found.isEmpty()) {
            log.debug("nothing to release for order {}", orderId);
            return false;
        }
        Reservation reservation = found.get();
        if (reservation.getStatus().isTerminal()) {
            log.debug("reservation for order {} is already {}", orderId, reservation.getStatus());
            return false;
        }

        releaseLines(reservation);
        reservation.release();

        outbox.write(AGGREGATE, orderId, EventTypes.INVENTORY_RELEASED, Topics.INVENTORY_RELEASED,
                new EventEnvelope.InventoryReleased(orderId, reservation.getReservationId(), reason), actor);
        log.info("released reservation {} for order {}: {}",
                reservation.getReservationId(), orderId, reason);
        return true;
    }

    /** Used by the expiry sweeper, which has already locked the reservation row. */
    @Transactional
    public void releaseExpired(Reservation reservation, Actor actor) {
        releaseLines(reservation);
        reservation.release();
        outbox.write(AGGREGATE, reservation.getOrderId(), EventTypes.INVENTORY_RELEASED,
                Topics.INVENTORY_RELEASED,
                new EventEnvelope.InventoryReleased(reservation.getOrderId(),
                        reservation.getReservationId(), "reservation expired"),
                actor);
    }

    private void releaseLines(Reservation reservation) {
        Map<UUID, InventoryLot> locked = lockLotsFor(reservation);
        for (ReservationLine line : reservation.getLines()) {
            locked.get(line.getLotId()).release(line.getQty());
        }
    }

    // -------------------------------------------------------------- consume --

    /**
     * Stock has physically shipped. On-hand and reserved drop together.
     *
     * <p>Quantities come from the reservation, not from the shipment event:
     * inventory owns what was held, and a shipment that disagrees is a bug worth
     * seeing in the logs rather than a reason to write the wrong number.
     */
    @Transactional
    public void consume(UUID orderId, List<EventEnvelope.ShipmentShipped.ShippedLine> shippedLines) {
        Optional<Reservation> found = reservations.lockByOrderId(orderId);
        if (found.isEmpty()) {
            log.warn("shipment for order {} has no reservation to consume", orderId);
            return;
        }
        Reservation reservation = found.get();
        if (reservation.getStatus() == com.freshchain.inventory.domain.ReservationStatus.CONSUMED) {
            log.debug("reservation for order {} already consumed", orderId);
            return;
        }
        if (reservation.getStatus() != com.freshchain.inventory.domain.ReservationStatus.CONFIRMED) {
            log.warn("shipment for order {} arrived while reservation was {}; not consuming",
                    orderId, reservation.getStatus());
            return;
        }

        warnOnMismatch(orderId, reservation, shippedLines);

        Map<UUID, InventoryLot> locked = lockLotsFor(reservation);
        for (ReservationLine line : reservation.getLines()) {
            locked.get(line.getLotId()).consume(line.getQty());
        }
        reservation.consume();
        log.info("consumed {} unit(s) against reservation {} for order {}",
                reservation.totalQty(), reservation.getReservationId(), orderId);
    }

    private void warnOnMismatch(UUID orderId, Reservation reservation,
                                List<EventEnvelope.ShipmentShipped.ShippedLine> shippedLines) {
        if (shippedLines == null || shippedLines.isEmpty()) {
            return;
        }
        int shipped = shippedLines.stream()
                .mapToInt(EventEnvelope.ShipmentShipped.ShippedLine::qty).sum();
        if (shipped != reservation.totalQty()) {
            log.warn("shipment for order {} reports {} unit(s) but the reservation holds {}",
                    orderId, shipped, reservation.totalQty());
        }
    }

    /** Locks every lot a reservation touches, in id order, to keep deadlocks out. */
    private Map<UUID, InventoryLot> lockLotsFor(Reservation reservation) {
        List<UUID> lotIds = reservation.getLines().stream()
                .map(ReservationLine::getLotId)
                .distinct()
                .sorted()
                .toList();
        Map<UUID, InventoryLot> locked = new LinkedHashMap<>();
        lots.lockByIds(lotIds).forEach(lot -> locked.put(lot.getLotId(), lot));
        return locked;
    }

    // ----------------------------------------------------------------- read --

    @Transactional(readOnly = true)
    public Optional<Reservation> findByOrderId(UUID orderId) {
        return reservations.findByOrderId(orderId)
                .map(reservation -> {
                    reservation.getLines().size(); // initialise inside the transaction
                    return reservation;
                });
    }
}
