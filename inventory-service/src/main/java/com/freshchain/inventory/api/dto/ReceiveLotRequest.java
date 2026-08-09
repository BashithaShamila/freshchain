package com.freshchain.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

@Schema(description = "A physical receipt of stock into one lot")
public record ReceiveLotRequest(
        @NotBlank @Schema(example = "CHK-BRST-5LB") String sku,
        @NotBlank @Schema(example = "WH-COL-01") String warehouseId,
        @Min(1) @Schema(example = "40") int qty,
        @Future @Schema(example = "2026-09-02") LocalDate expiryDate) {
}
