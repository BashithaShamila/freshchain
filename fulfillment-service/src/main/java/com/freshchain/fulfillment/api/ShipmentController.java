package com.freshchain.fulfillment.api;

import com.freshchain.events.Actor;
import com.freshchain.fulfillment.api.dto.ShipmentResponse;
import com.freshchain.fulfillment.service.FulfillmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipments", description = "Warehouse-side picking and dispatch")
public class ShipmentController {

    private final FulfillmentService fulfillment;

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR','ADMIN')")
    @Operation(summary = "Shipment for an order, with the lots to pick from")
    public ResponseEntity<ShipmentResponse> get(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ShipmentResponse.from(fulfillment.get(orderId)));
    }

    @PostMapping("/{orderId}/pick")
    @PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR','ADMIN')")
    @Operation(summary = "Mark a shipment as picked")
    public ResponseEntity<ShipmentResponse> pick(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ShipmentResponse.from(fulfillment.pick(orderId)));
    }

    @PostMapping("/{orderId}/ship")
    @PreAuthorize("hasAnyRole('WAREHOUSE_OPERATOR','ADMIN')")
    @Operation(summary = "Dispatch a shipment",
            description = "Publishes the event that finally decrements on-hand stock. Not reversible.")
    public ResponseEntity<ShipmentResponse> ship(@PathVariable UUID orderId, Authentication authentication) {
        return ResponseEntity.ok(ShipmentResponse.from(fulfillment.ship(orderId, actorFor(authentication))));
    }

    private static Actor actorFor(Authentication authentication) {
        if (authentication == null) {
            return Actor.SYSTEM;
        }
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .toList();
        return new Actor(authentication.getName(), roles);
    }
}
