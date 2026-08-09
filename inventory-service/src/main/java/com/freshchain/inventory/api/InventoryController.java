package com.freshchain.inventory.api;

import com.freshchain.inventory.api.dto.AvailabilityResponse;
import com.freshchain.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Availability by SKU, broken down by lot")
public class InventoryController {

    private final StockService stock;

    @GetMapping("/{sku}/availability")
    @Operation(summary = "Available quantity and lot breakdown for a SKU")
    public ResponseEntity<AvailabilityResponse> availability(
            @PathVariable String sku,
            @RequestParam(required = false) String warehouseId) {
        return ResponseEntity.ok(AvailabilityResponse.from(
                stock.product(sku), warehouseId, stock.lotsFor(sku, warehouseId)));
    }
}
