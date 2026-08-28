package com.freshchain.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Acknowledgement of a placed order, before allocation has run")
public record PlaceOrderResponse(
        OrderResponse order,
        @Schema(description = """
                Best-effort snapshot of available stock at the moment of placement,
                by SKU. Advisory only, and absent entirely if the inventory service
                did not answer in time. Allocation is what actually decides.""")
        Map<String, Integer> availabilityPreview) {
}
