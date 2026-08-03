package com.freshchain.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Product {

    @Id
    @Column(name = "sku", nullable = false, updatable = false)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "shelf_life_days", nullable = false)
    private int shelfLifeDays;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allergens", nullable = false, columnDefinition = "text[]")
    private String[] allergens;

    public Set<String> allergenSet() {
        if (allergens == null) {
            return Set.of();
        }
        return Arrays.stream(allergens)
                .filter(a -> a != null && !a.isBlank())
                .map(a -> a.toLowerCase().trim())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    /** The text the embedding model sees. Kept in one place so it cannot drift. */
    public String embeddableText() {
        return "%s. Category: %s. %s".formatted(name, category, description);
    }
}
