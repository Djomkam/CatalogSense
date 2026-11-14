package com.djomkam.catalogsense.catalog;

import java.math.BigDecimal;
import java.util.List;

/**
 * The values a product is created or updated with. A domain record, so {@link Product} doesn't depend
 * on the request format; ingestion converts its payload into this.
 *
 * @param version version from the source system; required, newer versions replace older ones
 */
public record ProductData(
        String name,
        String brand,
        String category,
        BigDecimal price,
        String currency,
        String imageUrl,
        long version,
        List<VariantData> variants
) {

    public ProductData {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public record VariantData(String sku, String color, String size, BigDecimal price, boolean inStock) {
    }
}
