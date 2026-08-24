package com.freshchain.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLine {

    @Id
    @Column(name = "order_line_id", nullable = false, updatable = false)
    private UUID orderLineId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "sku", nullable = false, updatable = false)
    private String sku;

    @Column(name = "qty_requested", nullable = false, updatable = false)
    private int qtyRequested;

    @Column(name = "qty_allocated", nullable = false)
    private int qtyAllocated;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private BigDecimal unitPrice;

    static OrderLine of(Order order, String sku, int qtyRequested, BigDecimal unitPrice) {
        OrderLine line = new OrderLine();
        line.orderLineId = UUID.randomUUID();
        line.order = order;
        line.sku = sku;
        line.qtyRequested = qtyRequested;
        line.qtyAllocated = 0;
        line.unitPrice = unitPrice;
        return line;
    }

    void allocate(int qty) {
        if (qty < 0 || qty > qtyRequested) {
            throw new IllegalArgumentException(
                    "cannot allocate %d against a line that requested %d".formatted(qty, qtyRequested));
        }
        this.qtyAllocated = qty;
    }

    public boolean isFullyAllocated() {
        return qtyAllocated == qtyRequested;
    }

    /** Priced on what was actually allocated, not on what was asked for. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(qtyAllocated));
    }
}
