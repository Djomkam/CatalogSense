package com.djomkam.catalogsense.search;

import com.djomkam.catalogsense.event.ProductSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * What a product looks like in the search index: flat and denormalised for querying.
 * Variant colors and sizes are copied up to the product so they can be used as facets.
 */
public record ProductDocument(
        String catalogId,
        String sku,
        String name,
        String brand,
        String category,
        BigDecimal price,
        String currency,
        String imageUrl,
        boolean inStock,
        long version,
        Instant updatedAt,
        List<String> colors,
        List<String> sizes
) {

    /** Same id as the Kafka message key, so one product is one document. */
    public static String id(String catalogId, String sku) {
        return catalogId + ":" + sku;
    }

    public String id() {
        return id(catalogId, sku);
    }

    public ProductDocument withCatalog(String otherCatalogId) {
        return new ProductDocument(otherCatalogId, sku, name, brand, category, price, currency, imageUrl, inStock,
                version, updatedAt, colors, sizes);
    }

    public static ProductDocument from(ProductSnapshot p) {
        return new ProductDocument(p.catalogId(), p.sku(), p.name(), p.brand(), p.category(), p.price(),
                p.currency(), p.imageUrl(), p.inStock(), p.version(), p.updatedAt(),
                distinct(p.variants().stream().map(ProductSnapshot.VariantSnapshot::color).toList()),
                distinct(p.variants().stream().map(ProductSnapshot.VariantSnapshot::size).toList()));
    }

    private static List<String> distinct(List<String> values) {
        return values.stream().filter(Objects::nonNull).filter(v -> !v.isBlank()).distinct().sorted().toList();
    }
}
