package com.freshchain.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Optional reason recorded against the cancellation")
public record CancelOrderRequest(
        @Schema(example = "customer changed their delivery date") String reason) {
}
