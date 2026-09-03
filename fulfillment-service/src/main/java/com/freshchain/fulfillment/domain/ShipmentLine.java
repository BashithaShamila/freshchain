package com.freshchain.fulfillment.domain;

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

/** A pick instruction: this many units, from this specific lot. */
@Entity
@Table(name = "shipment_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentLine {

    @Id
    @Column(name = "shipment_line_id", nullable = false, updatable = false)
    private UUID shipmentLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Column(name = "lot_id", nullable = false, updatable = false)
    private UUID lotId;

    @Column(name = "sku", nullable = false, updatable = false)
    private String sku;

    @Column(name = "qty", nullable = false, updatable = false)
    private int qty;

    static ShipmentLine of(Shipment shipment, UUID lotId, String sku, int qty) {
        ShipmentLine line = new ShipmentLine();
        line.shipmentLineId = UUID.randomUUID();
        line.shipment = shipment;
        line.lotId = lotId;
        line.sku = sku;
        line.qty = qty;
        return line;
    }
}
