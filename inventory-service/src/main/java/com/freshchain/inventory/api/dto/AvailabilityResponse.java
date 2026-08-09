package com.freshchain.inventory.api.dto;

import com.freshchain.inventory.domain.InventoryLot;
import com.freshchain.inventory.domain.Product;
import java.time.LocalDate;
import java.util.List;

/**
 * Availability is reported with its lot breakdown, not as a single number.
 * "60 cases available" hides that 45 of them expire on Tuesday, which is
 * exactly the thing a buyer needs to know.
 */
public record AvailabilityResponse(
        String sku,
        String name,
        String warehouseId,
        int totalOnHand,
        int totalReserved,
        int available,
        LocalDate nearestExpiry,
        List<LotResponse> lots) {

    public static AvailabilityResponse from(Product product, String warehouseId, List<InventoryLot> lots) {
        LocalDate today = LocalDate.now();
        List<InventoryLot> sellable = lots.stream().filter(lot -> !lot.isExpiredOn(today)).toList();

        return new AvailabilityResponse(
                product.getSku(),
                product.getName(),
                warehouseId,
                sellable.stream().mapToInt(InventoryLot::getQtyOnHand).sum(),
                sellable.stream().mapToInt(InventoryLot::getQtyReserved).sum(),
                sellable.stream().mapToInt(InventoryLot::available).sum(),
                sellable.stream().map(InventoryLot::getExpiryDate).min(LocalDate::compareTo).orElse(null),
                lots.stream().map(LotResponse::from).toList());
    }
}
