package com.freshchain.order.client;

import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The one synchronous call in the system.
 *
 * <p>Everything that changes state goes over Kafka. This does not: it is a
 * read-only preview shown at placement time so a customer can see they are
 * about to order forty cases of something with nine in stock. It is allowed to
 * fail — allocation is authoritative and happens asynchronously either way — so
 * a circuit breaker around it degrades the preview rather than the order.
 *
 * <p>Making this asynchronous too would have meant a request/reply topic and a
 * correlation store to answer one screen's worth of read. See docs/adr/ADR-001.
 */
@FeignClient(name = "inventory", url = "${freshchain.inventory.base-url}")
public interface InventoryClient {

    @GetMapping("/api/v1/inventory/{sku}/availability")
    AvailabilityView availability(@PathVariable("sku") String sku,
                                  @RequestParam("warehouseId") String warehouseId);

    /** Only the fields this service actually reads. */
    record AvailabilityView(String sku, String name, String warehouseId, int available, List<Lot> lots) {

        public record Lot(UUID lotId, int qtyOnHand, int qtyReserved, int available, String expiryDate) {
        }
    }
}
