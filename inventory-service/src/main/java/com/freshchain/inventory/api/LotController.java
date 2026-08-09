package com.freshchain.inventory.api;

import com.freshchain.inventory.api.dto.AdjustLotRequest;
import com.freshchain.inventory.api.dto.LotResponse;
import com.freshchain.inventory.api.dto.ReceiveLotRequest;
import com.freshchain.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/lots")
@RequiredArgsConstructor
@Tag(name = "Lots", description = "Receiving and write-offs")
public class LotController {

    private final StockService stock;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Receive stock into a new lot")
    public ResponseEntity<LotResponse> receive(@Valid @RequestBody ReceiveLotRequest request) {
        LotResponse lot = LotResponse.from(
                stock.receive(request.sku(), request.warehouseId(), request.qty(), request.expiryDate()));
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/lots/{lotId}").build(lot.lotId()))
                .body(lot);
    }

    @PostMapping("/{lotId}/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Adjust a lot for shrinkage, damage or a recount",
            description = "Refused if it would leave less on hand than is already reserved.")
    public ResponseEntity<LotResponse> adjust(@PathVariable UUID lotId,
                                              @Valid @RequestBody AdjustLotRequest request) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(LotResponse.from(stock.adjust(lotId, request.delta(), request.reason())));
    }
}
