package com.freshchain.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "A multi-line order against one warehouse")
public record PlaceOrderRequest(
        @NotBlank @Schema(example = "WH-COL-01") String warehouseId,
        @Schema(description = "Whether the customer will take less than they asked for", example = "true")
        boolean allowPartial,
        @NotEmpty @Valid List<Line> lines) {

    public record Line(
            @NotBlank @Schema(example = "CHK-BRST-5LB") String sku,
            @Min(1) @Schema(example = "40") int qty) {
    }
}
