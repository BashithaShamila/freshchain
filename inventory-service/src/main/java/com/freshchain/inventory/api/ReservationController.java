package com.freshchain.inventory.api;

import com.freshchain.inventory.api.dto.ReservationResponse;
import com.freshchain.inventory.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "What an order is holding, and from which lots")
public class ReservationController {

    private final ReservationService reservations;

    @GetMapping("/{orderId}")
    @Operation(summary = "Reservation detail for an order")
    public ResponseEntity<ReservationResponse> byOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(reservations.findByOrderId(orderId)
                .map(ReservationResponse::from)
                .orElseThrow(() -> new NoSuchElementException("no reservation for order " + orderId)));
    }
}
