package com.djomkam.catalogsense.search;

import com.djomkam.catalogsense.event.ProductEventMessage;
import com.djomkam.catalogsense.event.ProductSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * What a product looks like in the search index: flat and denormalised for querying.
 * Variant colors and sizes are copied up to the product so they can be used as facets.
 * The component names are the index field names (see {@link ProductFields}).
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

    /** Same as the Kafka message key, so one product is exactly one document. */
    public static String documentId(String catalogId, String sku) {
        return ProductEventMessage.key(catalogId, sku);
    }

    public String documentId() {
        return documentId(catalogId, sku);
    }

    public static ProductDocument from(ProductSnapshot product) {
        return new ProductDocument(product.catalogId(), product.sku(), product.name(), product.brand(),
                product.category(), product.price(), product.currency(), product.imageUrl(), product.inStock(),
                product.version(), product.updatedAt(),
                distinctNonBlankSorted(product.variants().stream().map(ProductSnapshot.VariantSnapshot::color).toList()),
                distinctNonBlankSorted(product.variants().stream().map(ProductSnapshot.VariantSnapshot::size).toList()));
    }

    private static List<String> distinctNonBlankSorted(List<String> values) {
        return values.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).distinct().sorted().toList();
    }
}
