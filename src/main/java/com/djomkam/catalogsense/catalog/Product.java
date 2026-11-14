package com.djomkam.catalogsense.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A product of one catalog, identified by (catalogId, sku). Its fields only change through
 * {@link #create} and {@link #updateWith}, which take already-validated {@link ProductData}.
 */
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "variants")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "catalog_id", nullable = false)
    private String catalogId;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String brand;
    private String category;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private String currency;

    @Column(name = "image_url")
    private String imageUrl;

    /** Version sent by the source system; updates with an older or equal version are ignored. */
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sku")
    private List<Variant> variants = new ArrayList<>();

    private Product(String catalogId, String sku) {
        this.catalogId = catalogId;
        this.sku = sku;
    }

    public static Product create(String catalogId, String sku, ProductData data, Instant updatedAt) {
        Product product = new Product(catalogId, sku);
        product.updateWith(data, updatedAt);
        return product;
    }

    public void updateWith(ProductData data, Instant updatedAt) {
        this.name = data.name();
        this.brand = data.brand();
        this.category = data.category();
        this.price = data.price();
        this.currency = data.currency();
        this.imageUrl = data.imageUrl();
        this.version = data.version();
        this.updatedAt = updatedAt;
        replaceVariants(data.variants().stream().map(Variant::from).toList());
    }

    /**
     * True if any variant is in stock. A product without variants is a simple product that is
     * sold as-is, so it counts as in stock.
     */
    public boolean isInStock() {
        return variants.isEmpty() || variants.stream().anyMatch(Variant::isInStock);
    }

    private void replaceVariants(List<Variant> newVariants) {
        variants.clear();
        newVariants.forEach(variant -> {
            variant.setProduct(this);
            variants.add(variant);
        });
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
        if (!(other instanceof Product otherProduct)) {
            return false;
        }
        return id != null && id.equals(otherProduct.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
