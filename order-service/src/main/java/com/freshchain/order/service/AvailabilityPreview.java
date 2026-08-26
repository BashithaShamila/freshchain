package com.freshchain.order.service;

import com.freshchain.order.api.dto.PlaceOrderRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

/**
 * Best-effort look at what is on the shelf, shown back with the 202. Never
 * blocks placement and never decides anything: if inventory is slow or down, the
 * order is still accepted and allocation still happens over Kafka.
 */
@Service
@Slf4j
public class AvailabilityPreview {

    private final com.freshchain.order.client.InventoryClient inventory;
    private final CircuitBreakerFactory<?, ?> circuitBreakers;
    private final boolean enabled;

    public AvailabilityPreview(com.freshchain.order.client.InventoryClient inventory,
                               CircuitBreakerFactory<?, ?> circuitBreakers,
                               @Value("${freshchain.inventory.preview-enabled:true}") boolean enabled) {
        this.inventory = inventory;
        this.circuitBreakers = circuitBreakers;
        this.enabled = enabled;
    }

    public Map<String, Integer> forOrder(PlaceOrderRequest request) {
        if (!enabled) {
            return Map.of();
        }
        Map<String, Integer> preview = new LinkedHashMap<>();
        for (PlaceOrderRequest.Line line : request.lines()) {
            Integer available = circuitBreakers.create("inventory").run(
                    () -> inventory.availability(line.sku(), request.warehouseId()).available(),
                    throwable -> {
                        log.debug("availability preview unavailable for {}: {}",
                                line.sku(), throwable.toString());
                        return null;
                    });
            if (available != null) {
                preview.put(line.sku(), available);
            }
        }
        return preview;
    }
}
