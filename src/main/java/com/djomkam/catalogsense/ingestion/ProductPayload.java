package com.djomkam.catalogsense.ingestion;

import com.djomkam.catalogsense.catalog.ProductData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

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

    /**
     * The same payload with cosmetic differences removed: trimmed name, trimmed uppercase currency,
     * and an explicit in-stock flag on every variant. Done once before validation, so the rules and
     * the stored product see the same values.
     */
    public ProductPayload normalized() {
        List<VariantPayload> normalizedVariants = variants == null ? null
                : variants.stream().map(VariantPayload::normalized).toList();
        return new ProductPayload(sku, trimOrNull(name), brand, category, price,
                currency == null ? null : currency.trim().toUpperCase(Locale.ROOT),
                imageUrl, version, normalizedVariants);
    }

    /**
     * Converts an accepted payload into the values the product is stored with.
     *
     * @throws IllegalArgumentException if the version is missing (the required-fields rule rejects
     *                                  such payloads, so an accepted one always has it)
     */
    public ProductData toProductData() {
        if (version == null) {
            throw new IllegalArgumentException("Product " + sku + " has no version");
        }
        return new ProductData(name, brand, category, price, currency, imageUrl, version,
                variantsOrEmpty().stream()
                        .map(variant -> new ProductData.VariantData(variant.sku(), variant.color(), variant.size(),
                                variant.price(), variant.isInStockOrDefault()))
                        .toList());
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }
}
