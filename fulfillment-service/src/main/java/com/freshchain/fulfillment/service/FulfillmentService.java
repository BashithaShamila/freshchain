package com.freshchain.fulfillment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshchain.events.Actor;
import com.freshchain.events.EventEnvelope;
import com.freshchain.events.EventTypes;
import com.freshchain.events.Topics;
import com.freshchain.fulfillment.domain.OrderAllocation;
import com.freshchain.fulfillment.domain.Shipment;
import com.freshchain.fulfillment.repository.OrderAllocationRepository;
import com.freshchain.fulfillment.repository.ShipmentRepository;
import com.freshchain.fulfillment.support.OutboxWriter;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FulfillmentService {

    private static final String AGGREGATE = "Shipment";
    private static final TypeReference<List<AllocatedLine>> LINES_TYPE = new TypeReference<>() {
    };

    private final ShipmentRepository shipments;
    private final OrderAllocationRepository allocations;
    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;

    /** A line of an allocation as this service stores it. */
    public record AllocatedLine(UUID lotId, String sku, int qty) {
    }

    // ------------------------------------------------- assembling the facts --

    @Transactional
    public void recordAllocation(EventEnvelope.InventoryReserved reserved) {
        if (reserved.reservationId() == null) {
            // Rejected at allocation: there is nothing to ship, ever.
            return;
        }
        List<AllocatedLine> lines = reserved.lines() == null ? List.of() : reserved.lines().stream()
                .flatMap(line -> line.lots().stream()
                        .map(lot -> new AllocatedLine(lot.lotId(), line.sku(), lot.qty())))
                .toList();
        if (lines.isEmpty()) {
            return;
        }

        OrderAllocation allocation = loadOrCreate(reserved.orderId());
        allocation.recordAllocation(reserved.reservationId(), reserved.customerId(),
                reserved.warehouseId(), writeLines(lines));
        materialiseIfReady(allocation);
    }

    @Transactional
    public void recordConfirmation(UUID orderId, UUID customerId) {
        OrderAllocation allocation = loadOrCreate(orderId);
        allocation.recordConfirmation(customerId);
        materialiseIfReady(allocation);
    }

    /**
     * Creates the shipment once both the allocation and the confirmation have
     * arrived, whichever order they came in.
     */
    private void materialiseIfReady(OrderAllocation allocation) {
        if (!allocation.isReadyToShip()) {
            log.debug("order {} not ready for a shipment yet (allocated={}, confirmed={})",
                    allocation.getOrderId(), allocation.isAllocated(), allocation.isConfirmed());
            return;
        }
        if (shipments.existsByOrderId(allocation.getOrderId())) {
            allocation.markShipmentCreated();
            return;
        }

        Shipment shipment = Shipment.create(
                allocation.getOrderId(), allocation.getCustomerId(), allocation.getWarehouseId());
        readLines(allocation).forEach(line -> shipment.addLine(line.lotId(), line.sku(), line.qty()));
        shipments.save(shipment);
        allocation.markShipmentCreated();

        log.info("created shipment {} for order {} with {} line(s)",
                shipment.getShipmentId(), shipment.getOrderId(), shipment.getLines().size());
    }

    // ------------------------------------------------------------ lifecycle --

    @Transactional
    public Shipment pick(UUID orderId) {
        Shipment shipment = lock(orderId);
        shipment.pick();
        log.info("picked shipment {} for order {}", shipment.getShipmentId(), orderId);
        return shipment;
    }

    /**
     * The event this publishes is what makes inventory finally decrement
     * on-hand, so it is written to the outbox in the same transaction as the
     * status change. Neither can happen without the other.
     */
    @Transactional
    public Shipment ship(UUID orderId, Actor actor) {
        Shipment shipment = lock(orderId);
        String trackingRef = "FC-" + shipment.getShipmentId().toString().substring(0, 8).toUpperCase();
        shipment.ship(trackingRef);

        outbox.write(AGGREGATE, orderId, EventTypes.SHIPMENT_SHIPPED, Topics.FULFILLMENT_SHIPPED,
                new EventEnvelope.ShipmentShipped(
                        orderId,
                        shipment.getShipmentId(),
                        trackingRef,
                        shipment.getLines().stream()
                                .map(line -> new EventEnvelope.ShipmentShipped.ShippedLine(
                                        line.getLotId(), line.getSku(), line.getQty()))
                                .toList()),
                actor);

        log.info("shipped {} for order {} as {}", shipment.getShipmentId(), orderId, trackingRef);
        return shipment;
    }

    /** An order cancelled before the truck left takes its shipment with it. */
    @Transactional
    public void cancel(UUID orderId) {
        shipments.lockByOrderId(orderId).ifPresent(shipment -> {
            if (shipment.isShipped()) {
                log.error("order {} was cancelled but shipment {} has already gone out",
                        orderId, shipment.getShipmentId());
                return;
            }
            shipment.cancel();
            log.info("cancelled shipment {} for order {}", shipment.getShipmentId(), orderId);
        });
    }

    @Transactional(readOnly = true)
    public Shipment get(UUID orderId) {
        Shipment shipment = shipments.findByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("no shipment for order " + orderId));
        shipment.getLines().size(); // initialise inside the transaction
        return shipment;
    }

    // --------------------------------------------------------------- helper --

    private Shipment lock(UUID orderId) {
        Shipment shipment = shipments.lockByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("no shipment for order " + orderId));
        shipment.getLines().size();
        return shipment;
    }

    private OrderAllocation loadOrCreate(UUID orderId) {
        return allocations.lockByOrderId(orderId)
                .orElseGet(() -> allocations.save(OrderAllocation.forOrder(orderId)));
    }

    private String writeLines(List<AllocatedLine> lines) {
        try {
            return objectMapper.writeValueAsString(lines);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not store allocated lines", e);
        }
    }

    private List<AllocatedLine> readLines(OrderAllocation allocation) {
        try {
            return objectMapper.readValue(allocation.getLines(), LINES_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "could not read allocated lines for order " + allocation.getOrderId(), e);
        }
    }
}
