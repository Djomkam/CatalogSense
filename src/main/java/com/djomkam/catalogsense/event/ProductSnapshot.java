package com.djomkam.catalogsense.event;

import com.djomkam.catalogsense.domain.Product;
import com.djomkam.catalogsense.domain.Variant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Immutable copy of a product, taken inside the ingestion transaction.
 *
 * <p>Events carry snapshots instead of JPA entities: consumers can read them after the
 * transaction has committed (no lazy-loading surprises), and they don't depend on the
 * persistence model.</p>
 */
public record ProductSnapshot(
        String catalogId,
        String sku,
        String name,
        String brand,
        String category,
        BigDecimal price,
        String currency,
        String imageUrl,
        long version,
        boolean inStock,
        Instant updatedAt,
        List<VariantSnapshot> variants
) {

    public ProductSnapshot {
        variants = List.copyOf(variants);
    }

    public record VariantSnapshot(String sku, String color, String size, BigDecimal price, boolean inStock) {

        static VariantSnapshot of(Variant v) {
            return new VariantSnapshot(v.getSku(), v.getColor(), v.getSize(), v.getPrice(), v.isInStock());
        }
    }

    /** Must be called while the persistence context is open, since it reads the variants. */
    public static ProductSnapshot of(Product p) {
        return new ProductSnapshot(p.getCatalogId(), p.getSku(), p.getName(), p.getBrand(), p.getCategory(),
                p.getPrice(), p.getCurrency(), p.getImageUrl(), p.getVersion(), p.isInStock(), p.getUpdatedAt(),
                p.getVariants().stream().map(VariantSnapshot::of).toList());
    }
}
