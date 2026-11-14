package com.djomkam.catalogsense.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/** A sellable variation of a product (color, size). Created and replaced only through its {@link Product}. */
@Entity
@Table(name = "variant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "product")
public class Variant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    @Setter(AccessLevel.PACKAGE)
    private Product product;

    @Column(nullable = false)
    private String sku;

    private String color;
    private String size;

    /** Optional override of the product price. */
    private BigDecimal price;

    @Column(name = "in_stock", nullable = false)
    private boolean inStock;

    private Variant(String sku, String color, String size, BigDecimal price, boolean inStock) {
        this.sku = sku;
        this.color = color;
        this.size = size;
        this.price = price;
        this.inStock = inStock;
    }

    static Variant from(ProductData.VariantData data) {
        return new Variant(data.sku(), data.color(), data.size(), data.price(), data.inStock());
    }

    /**
     * Entity identity is the database id. Two unsaved instances (id == null) are never equal,
     * and the hash code is stable across persist, as recommended for JPA entities.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Variant otherVariant)) {
            return false;
        }
        return id != null && id.equals(otherVariant.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
