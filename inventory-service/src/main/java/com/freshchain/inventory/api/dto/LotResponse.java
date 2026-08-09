package com.freshchain.inventory.api.dto;

import com.freshchain.inventory.domain.InventoryLot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LotResponse(
        UUID lotId,
        String sku,
        String warehouseId,
        int qtyOnHand,
        int qtyReserved,
        int available,
        LocalDate expiryDate,
        Instant receivedAt) {

    public static LotResponse from(InventoryLot lot) {
        return new LotResponse(
                lot.getLotId(), lot.getSku(), lot.getWarehouseId(),
                lot.getQtyOnHand(), lot.getQtyReserved(), lot.available(),
                lot.getExpiryDate(), lot.getReceivedAt());
    }
}
