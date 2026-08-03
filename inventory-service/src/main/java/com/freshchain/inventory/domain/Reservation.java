package com.freshchain.inventory.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Held stock for one order. The unique constraint on {@code order_id} is a
 * second idempotency guard behind the processed-event table: even if the same
 * order were somehow delivered under two different event ids, only one
 * reservation can exist.
 */
@Entity
@Table(name = "reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @Column(name = "reservation_id", nullable = false, updatable = false)
    private UUID reservationId;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private String warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ReservationLine> lines = new ArrayList<>();

    public static Reservation hold(UUID orderId, UUID customerId, String warehouseId, Duration ttl) {
        Reservation reservation = new Reservation();
        Instant now = Instant.now();
        reservation.reservationId = UUID.randomUUID();
        reservation.orderId = orderId;
        reservation.customerId = customerId;
        reservation.warehouseId = warehouseId;
        reservation.status = ReservationStatus.HELD;
        reservation.createdAt = now;
        reservation.updatedAt = now;
        reservation.expiresAt = now.plus(ttl);
        return reservation;
    }

    public void addLine(LotPick pick) {
        ReservationLine line = ReservationLine.of(this, pick);
        lines.add(line);
    }

    public List<ReservationLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    /** Payment cleared. Expiry stops applying, so the sweeper will skip this row. */
    public void confirm() {
        requireStatus(ReservationStatus.HELD, "confirm");
        status = ReservationStatus.CONFIRMED;
        touch();
    }

    /**
     * Cancelled or timed out. Reachable from HELD and from CONFIRMED — an order
     * can be cancelled after payment clears but before the truck leaves — but
     * never from CONSUMED, because that stock has physically gone.
     */
    public void release() {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "cannot release reservation %s: status is already %s".formatted(reservationId, status));
        }
        status = ReservationStatus.RELEASED;
        touch();
    }

    /** Stock has shipped. */
    public void consume() {
        requireStatus(ReservationStatus.CONFIRMED, "consume");
        status = ReservationStatus.CONSUMED;
        touch();
    }

    public boolean isHeld() {
        return status == ReservationStatus.HELD;
    }

    public boolean hasExpiredAt(Instant now) {
        return isHeld() && expiresAt.isBefore(now);
    }

    public int totalQty() {
        return lines.stream().mapToInt(ReservationLine::getQty).sum();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    private void requireStatus(ReservationStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "cannot %s reservation %s: status is %s, expected %s"
                            .formatted(action, reservationId, status, expected));
        }
    }
}
