package com.freshchain.inventory.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Shrinkage, damage or a recount against an existing lot")
public record AdjustLotRequest(
        @Schema(example = "-5", description = "Signed change to on-hand quantity") int delta,
        @NotBlank @Schema(example = "cold chain breach on inbound pallet") String reason) {
}
