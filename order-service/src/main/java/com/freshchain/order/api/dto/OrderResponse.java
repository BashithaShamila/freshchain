package com.freshchain.order.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.freshchain.events.EventEnvelope;
import com.freshchain.order.domain.Order;
import com.freshchain.order.domain.OrderLine;
import com.freshchain.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        UUID orderId,
        UUID customerId,
        String warehouseId,
        OrderStatus status,
        String statusReason,
        boolean allowPartial,
        UUID reservationId,
        Instant reservationExpiresAt,
        BigDecimal total,
        Instant placedAt,
        Instant updatedAt,
        List<Line> lines,
        /** Advice from the inventory service when a line came up short. Never binding. */
        List<EventEnvelope.InventoryReserved.Substitution> substitutions) {

    public record Line(String sku, int qtyRequested, int qtyAllocated, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public static OrderResponse from(Order order, List<EventEnvelope.InventoryReserved.Substitution> substitutions) {
        return new OrderResponse(
                order.getOrderId(),
                order.getCustomerId(),
                order.getWarehouseId(),
                order.getStatus(),
                order.getStatusReason(),
                order.isAllowPartial(),
                order.getReservationId(),
                order.getReservationExpiresAt(),
                order.total(),
                order.getPlacedAt(),
                order.getUpdatedAt(),
                order.getLines().stream().map(OrderResponse::toLine).toList(),
                substitutions);
    }

    private static Line toLine(OrderLine line) {
        return new Line(line.getSku(), line.getQtyRequested(), line.getQtyAllocated(),
                line.getUnitPrice(), line.lineTotal());
    }
}
