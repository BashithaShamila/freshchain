package com.freshchain.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Static list price. A pricing engine is explicitly out of scope. */
@Entity
@Table(name = "sku_price")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkuPrice {

    @Id
    @Column(name = "sku", nullable = false, updatable = false)
    private String sku;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;
}
