package com.freshchain.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshchain.events.Actor;
import com.freshchain.events.AllocationStatus;
import com.freshchain.events.EventEnvelope;
import com.freshchain.events.EventTypes;
import com.freshchain.events.Topics;
import com.freshchain.order.api.dto.PlaceOrderRequest;
import com.freshchain.order.domain.Order;
import com.freshchain.order.domain.OrderStatus;
import com.freshchain.order.domain.Requester;
import com.freshchain.order.domain.SkuPrice;
import com.freshchain.order.repository.OrderRepository;
import com.freshchain.order.repository.SkuPriceRepository;
import com.freshchain.order.support.OutboxWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The order aggregate's application service, and the saga's origin point. */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final String AGGREGATE = "Order";

    private final OrderRepository orders;
    private final SkuPriceRepository prices;
    private final OutboxWriter outbox;
    private final ObjectMapper objectMapper;

    // ---------------------------------------------------------------- place --

    /**
     * The order row and the OrderPlaced outbox row are written in one
     * transaction. That is the whole point of the outbox: there is no window in
     * which an order exists but nobody was told, and none in which an event was
     * published for an order that rolled back.
     */
    @Transactional
    public Order place(Requester requester, PlaceOrderRequest request) {
        Order order = Order.place(requester.userId(), request.warehouseId(), request.allowPartial());
        for (PlaceOrderRequest.Line line : request.lines()) {
            BigDecimal unitPrice = prices.findById(line.sku())
                    .map(SkuPrice::getUnitPrice)
                    .orElseThrow(() -> new NoSuchElementException("no price on file for sku " + line.sku()));
            order.addLine(line.sku(), line.qty(), unitPrice);
        }
        orders.save(order);

        outbox.write(AGGREGATE, order.getOrderId(), EventTypes.ORDER_PLACED, Topics.ORDERS_PLACED,
                new EventEnvelope.OrderPlaced(
                        order.getOrderId(),
                        order.getCustomerId(),
                        order.getWarehouseId(),
                        order.isAllowPartial(),
                        request.lines().stream()
                                .map(line -> new EventEnvelope.OrderPlaced.Line(line.sku(), line.qty()))
                                .toList()),
                actorFor(requester));

        log.info("placed order {} for customer {} with {} line(s)",
                order.getOrderId(), requester.userId(), order.getLines().size());
        return order;
    }

    // -------------------------------------------------------------- confirm --

    /** Stands in for payment capture, which is out of scope. */
    @Transactional
    public Order confirm(UUID orderId, Requester requester) {
        Order order = loadOwned(orderId, requester);
        order.confirm();
        outbox.write(AGGREGATE, orderId, EventTypes.ORDER_CONFIRMED, Topics.ORDERS_CONFIRMED,
                new EventEnvelope.OrderConfirmed(orderId, order.getCustomerId()), actorFor(requester));
        log.info("confirmed order {}", orderId);
        return order;
    }

    // --------------------------------------------------------------- cancel --

    /** Triggers the compensating path: inventory hands the held stock back. */
    @Transactional
    public Order cancel(UUID orderId, String reason, Requester requester) {
        Order order = loadOwned(orderId, requester);
        String recorded = reason == null || reason.isBlank() ? "cancelled by customer" : reason;
        order.cancel(recorded);
        outbox.write(AGGREGATE, orderId, EventTypes.ORDER_CANCELLED, Topics.ORDERS_CANCELLED,
                new EventEnvelope.OrderCancelled(orderId, order.getCustomerId(), recorded),
                actorFor(requester));
        log.info("cancelled order {}: {}", orderId, recorded);
        return order;
    }

    // ----------------------------------------------------- inbound reactions --

    /** Applies the allocation outcome that came back from inventory. */
    @Transactional
    public void applyAllocation(EventEnvelope.InventoryReserved reserved) {
        Order order = orders.lockById(reserved.orderId()).orElse(null);
        if (order == null) {
            log.warn("allocation arrived for unknown order {}", reserved.orderId());
            return;
        }

        order.applyAllocation(
                toOrderStatus(reserved.status()),
                reserved.reservationId(),
                reserved.expiresAt(),
                writeAdvice(reserved.substitutions()));

        if (reserved.lines() != null) {
            reserved.lines().forEach(line -> order.allocateLine(line.sku(), line.qtyAllocated()));
        }
        log.info("order {} is now {}", order.getOrderId(), order.getStatus());
    }

    /**
     * Inventory gave the stock back. If the order is not already dead, this is
     * the compensating end of the saga and the order cannot stand.
     */
    @Transactional
    public void applyRelease(EventEnvelope.InventoryReleased released) {
        Order order = orders.lockById(released.orderId()).orElse(null);
        if (order == null || order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        if (order.getStatus() == OrderStatus.SHIPPED) {
            // Stock released against a shipped order means the two services
            // disagree about reality. Do not paper over it.
            log.error("order {} is shipped but its stock was released: {}",
                    released.orderId(), released.reason());
            return;
        }
        order.cancel("stock released: " + released.reason());
        log.info("order {} cancelled by compensation: {}", released.orderId(), released.reason());
    }

    @Transactional
    public void applyShipment(UUID orderId) {
        orders.lockById(orderId).ifPresentOrElse(
                order -> {
                    order.markShipped();
                    log.info("order {} shipped", orderId);
                },
                () -> log.warn("shipment arrived for unknown order {}", orderId));
    }

    // ----------------------------------------------------------------- read --

    @Transactional(readOnly = true)
    public Order get(UUID orderId, Requester requester) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("no order " + orderId));
        requireOwnership(order, requester);
        order.getLines().size(); // initialise inside the transaction
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> listForCustomer(Requester requester, int limit) {
        List<Order> page = orders.findByCustomerIdOrderByPlacedAtDesc(
                requester.userId(), org.springframework.data.domain.PageRequest.of(0, limit));
        // @BatchSize on the collection turns what would be one query per order
        // into a single batched follow-up.
        page.forEach(order -> order.getLines().size());
        return page;
    }

    // ---------------------------------------------------------------- helper --

    /** Locking load, for the paths that are about to change the order. */
    private Order loadOwned(UUID orderId, Requester requester) {
        Order order = orders.lockById(orderId)
                .orElseThrow(() -> new NoSuchElementException("no order " + orderId));
        requireOwnership(order, requester);
        order.getLines().size(); // initialise inside the transaction
        return order;
    }

    /**
     * A customer reading another customer's order gets 403, not 404: they are
     * authenticated and the order does exist, they simply may not see it.
     * Admins are exempt.
     */
    private void requireOwnership(Order order, Requester requester) {
        if (!order.isOwnedBy(requester.userId()) && !requester.isAdmin()) {
            log.warn("customer {} was refused access to order {}",
                    requester.userId(), order.getOrderId());
            throw new AccessDeniedException("this order belongs to another customer");
        }
    }

    private static OrderStatus toOrderStatus(AllocationStatus status) {
        return switch (status) {
            case FULL -> OrderStatus.ALLOCATED;
            case PARTIAL -> OrderStatus.PARTIALLY_ALLOCATED;
            case REJECTED -> OrderStatus.REJECTED;
        };
    }

    private static Actor actorFor(Requester requester) {
        return new Actor(String.valueOf(requester.userId()), List.copyOf(requester.roles()));
    }

    private String writeAdvice(List<EventEnvelope.InventoryReserved.Substitution> substitutions) {
        if (substitutions == null || substitutions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(substitutions);
        } catch (JsonProcessingException e) {
            log.warn("could not store substitution advice", e);
            return null;
        }
    }
}
