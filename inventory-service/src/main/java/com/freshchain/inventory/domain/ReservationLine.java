package com.freshchain.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One reservation against one lot. A single order line can span several of these. */
@Entity
@Table(name = "reservation_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationLine {

    @Id
    @Column(name = "reservation_line_id", nullable = false, updatable = false)
    private UUID reservationLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "lot_id", nullable = false, updatable = false)
    private UUID lotId;

    @Column(name = "sku", nullable = false, updatable = false)
    private String sku;

    @Column(name = "qty", nullable = false)
    private int qty;

    static ReservationLine of(Reservation reservation, LotPick pick) {
        ReservationLine line = new ReservationLine();
        line.reservationLineId = UUID.randomUUID();
        line.reservation = reservation;
        line.lotId = pick.lotId();
        line.sku = pick.sku();
        line.qty = pick.qty();
        return line;
    }
}
