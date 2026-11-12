package com.djomkam.catalogsense.domain;

import com.djomkam.catalogsense.api.ProductPayload;
import com.djomkam.catalogsense.api.VariantPayload;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "variants")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Include
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
    @Setter(AccessLevel.NONE)
    private List<Variant> variants = new ArrayList<>();

    public Product(String catalogId, String sku) {
        this.catalogId = catalogId;
        this.sku = sku;
    }

    public static Product create(String catalogId, String sku, ProductPayload payload, Instant updatedAt) {
        Product product = new Product(catalogId, sku);
        product.updateFromPayload(payload, updatedAt);
        return product;
    }

    public void updateFromPayload(ProductPayload payload, Instant updatedAt) {
        this.name = payload.name() != null ? payload.name().trim() : null;
        this.brand = payload.brand();
        this.category = payload.category();
        this.price = payload.price();
        this.currency = payload.currency() != null ? payload.currency().trim().toUpperCase(Locale.ROOT) : null;
        this.imageUrl = payload.imageUrl();
        this.version = payload.version() != null ? payload.version() : 0L;
        this.updatedAt = updatedAt;
        this.replaceVariants(payload.variantsOrEmpty().stream()
                .map(Product::toVariant)
                .toList());
    }

    private static Variant toVariant(VariantPayload v) {
        return new Variant(v.sku(), v.color(), v.size(), v.price(), v.inStock() == null || v.inStock());
    }

    public void replaceVariants(List<Variant> newVariants) {
        variants.clear();
        newVariants.forEach(v -> {
            v.setProduct(this);
            variants.add(v);
        });
    }

    public boolean isInStock() {
        return variants.isEmpty() || variants.stream().anyMatch(Variant::isInStock);
    }
}
