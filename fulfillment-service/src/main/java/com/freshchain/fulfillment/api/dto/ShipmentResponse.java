package com.freshchain.fulfillment.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.freshchain.fulfillment.domain.Shipment;
import com.freshchain.fulfillment.domain.ShipmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShipmentResponse(
        UUID shipmentId,
        UUID orderId,
        String warehouseId,
        ShipmentStatus status,
        String trackingRef,
        Instant createdAt,
        Instant pickedAt,
        Instant shippedAt,
        int totalQty,
        List<Line> lines) {

    public record Line(UUID lotId, String sku, int qty) {
    }

    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getShipmentId(),
                shipment.getOrderId(),
                shipment.getWarehouseId(),
                shipment.getStatus(),
                shipment.getTrackingRef(),
                shipment.getCreatedAt(),
                shipment.getPickedAt(),
                shipment.getShippedAt(),
                shipment.getLines().stream().mapToInt(line -> line.getQty()).sum(),
                shipment.getLines().stream()
                        .map(line -> new Line(line.getLotId(), line.getSku(), line.getQty()))
                        .toList());
    }
}
