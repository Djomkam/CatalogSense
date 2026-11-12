package com.djomkam.catalogsense.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * A product as sent by a merchant or a connector (e.g. Shopify).
 * Fields are deliberately not bean-validated: bad data is expected input,
 * and the Data Health rules turn it into actionable issues instead of a 400.
 */
public record ProductPayload(
        String sku,
        String name,
        String brand,
        String category,
        BigDecimal price,
        String currency,
        String imageUrl,
        Long version,
        List<VariantPayload> variants) {

    public List<VariantPayload> variantsOrEmpty() {
        return variants == null ? List.of() : variants;
    }
}
