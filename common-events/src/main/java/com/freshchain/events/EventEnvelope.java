package com.freshchain.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Common envelope for every event on every topic.
 *
 * @param eventId       drives consumer idempotency; a redelivery carries the same id
 * @param schemaVersion lets a consumer reject a payload shape it was not built for
 * @param traceId       propagates the Micrometer trace across the async hop
 * @param actor         carries identity across Kafka
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID aggregateId,
        String traceId,
        Actor actor,
        T payload) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static <T> EventEnvelope<T> of(String eventType, UUID aggregateId, String traceId, Actor actor, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                CURRENT_SCHEMA_VERSION,
                Instant.now(),
                aggregateId,
                traceId,
                actor == null ? Actor.SYSTEM : actor,
                payload);
    }

    // ------------------------------------------------------------- payloads --

    public record OrderPlaced(
            UUID orderId,
            UUID customerId,
            String warehouseId,
            boolean allowPartial,
            List<Line> lines) {

        public record Line(String sku, int qty) {
        }
    }

    public record InventoryReserved(
            UUID orderId,
            UUID customerId,
            String warehouseId,
            UUID reservationId,
            AllocationStatus status,
            Instant expiresAt,
            List<ReservedLine> lines,
            List<Substitution> substitutions) {

        /** Per-line outcome; the order-level {@code status} is the weakest line outcome. */
        public record ReservedLine(
                String sku,
                int qtyRequested,
                int qtyAllocated,
                List<LotAllocation> lots) {
        }

        public record LotAllocation(UUID lotId, int qty, String expiryDate) {
        }

        /**
         * Advisory only. Produced by similarity search, then filtered by the
         * deterministic allergen guard before it is ever allowed onto this record.
         */
        public record Substitution(
                String forSku,
                String suggestedSku,
                String suggestedName,
                int availableQty,
                double similarity,
                String rationale) {
        }
    }

    public record OrderConfirmed(UUID orderId, UUID customerId) {
    }

    public record OrderCancelled(UUID orderId, UUID customerId, String reason) {
    }

    public record InventoryReleased(UUID orderId, UUID reservationId, String reason) {
    }

    public record ShipmentShipped(
            UUID orderId,
            UUID shipmentId,
            String trackingRef,
            List<ShippedLine> lines) {

        public record ShippedLine(UUID lotId, String sku, int qty) {
        }
    }
}
