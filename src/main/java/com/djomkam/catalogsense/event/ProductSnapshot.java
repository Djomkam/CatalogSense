package com.djomkam.catalogsense.event;

import com.djomkam.catalogsense.catalog.Product;
import com.djomkam.catalogsense.catalog.Variant;

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

        static VariantSnapshot of(Variant variant) {
            return new VariantSnapshot(variant.getSku(), variant.getColor(), variant.getSize(), variant.getPrice(),
                    variant.isInStock());
        }
    }

    /** Must be called while the persistence context is open, since it reads the variants. */
    public static ProductSnapshot of(Product product) {
        return new ProductSnapshot(product.getCatalogId(), product.getSku(), product.getName(), product.getBrand(),
                product.getCategory(), product.getPrice(), product.getCurrency(), product.getImageUrl(),
                product.getVersion(), product.isInStock(), product.getUpdatedAt(),
                product.getVariants().stream().map(VariantSnapshot::of).toList());
    }
}
