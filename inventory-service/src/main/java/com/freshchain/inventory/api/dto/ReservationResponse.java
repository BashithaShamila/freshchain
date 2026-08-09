package com.freshchain.inventory.api.dto;

import com.freshchain.inventory.domain.Reservation;
import com.freshchain.inventory.domain.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        UUID orderId,
        String warehouseId,
        ReservationStatus status,
        Instant expiresAt,
        Instant createdAt,
        int totalQty,
        List<Line> lines) {

    public record Line(UUID lotId, String sku, int qty) {
    }

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getOrderId(),
                reservation.getWarehouseId(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt(),
                reservation.totalQty(),
                reservation.getLines().stream()
                        .map(line -> new Line(line.getLotId(), line.getSku(), line.getQty()))
                        .toList());
    }
}
