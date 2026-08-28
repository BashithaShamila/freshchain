package com.freshchain.order.api;

import com.freshchain.order.api.dto.CancelOrderRequest;
import com.freshchain.order.api.dto.OrderResponse;
import com.freshchain.order.api.dto.PlaceOrderRequest;
import com.freshchain.order.api.dto.PlaceOrderResponse;
import com.freshchain.order.domain.Order;
import com.freshchain.order.domain.Requester;
import com.freshchain.order.service.AvailabilityPreview;
import com.freshchain.order.service.OrderService;
import com.freshchain.order.support.OrderMdc;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Placement, confirmation and cancellation")
public class OrderController {

    private final OrderService orders;
    private final AvailabilityPreview preview;
    private final OrderMapper mapper;

    /**
     * Answers <b>202 Accepted</b>, not 201.
     *
     * <p>The order exists, but nothing has been allocated yet — that happens
     * asynchronously, over Kafka, and may come back full, partial or rejected.
     * 201 Created would promise a finished resource this endpoint cannot yet
     * describe. 202 is the honest representation of the architecture.
     */
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Place an order",
            description = "Accepted for allocation. Poll GET /api/v1/orders/{orderId} for the outcome.")
    public ResponseEntity<PlaceOrderResponse> place(@Valid @RequestBody PlaceOrderRequest request,
                                                    Authentication authentication) {
        Requester requester = Requesters.from(authentication);
        Order order = orders.place(requester, request);
        return OrderMdc.with(order.getOrderId(), () -> ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .location(UriComponentsBuilder.fromPath("/api/v1/orders/{orderId}").build(order.getOrderId()))
                .body(new PlaceOrderResponse(mapper.toResponse(order), preview.forOrder(request))));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @Operation(summary = "Order detail, including allocation state and any substitution advice")
    public ResponseEntity<OrderResponse> get(@PathVariable UUID orderId, Authentication authentication) {
        return ResponseEntity.ok(mapper.toResponse(orders.get(orderId, Requesters.from(authentication))));
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "The caller's own orders, most recent first")
    public ResponseEntity<List<OrderResponse>> mine(@RequestParam(defaultValue = "20") int limit,
                                                     Authentication authentication) {
        return ResponseEntity.ok(orders.listForCustomer(Requesters.from(authentication), Math.min(limit, 100))
                .stream().map(mapper::toResponse).toList());
    }

    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Confirm an allocated order",
            description = "Stands in for payment capture, which is out of scope.")
    public ResponseEntity<OrderResponse> confirm(@PathVariable UUID orderId, Authentication authentication) {
        return ResponseEntity.ok(mapper.toResponse(orders.confirm(orderId, Requesters.from(authentication))));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Cancel an order and release any stock it is holding")
    public ResponseEntity<OrderResponse> cancel(@PathVariable UUID orderId,
                                                 @RequestBody(required = false) CancelOrderRequest request,
                                                 Authentication authentication) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(
                mapper.toResponse(orders.cancel(orderId, reason, Requesters.from(authentication))));
    }
}
