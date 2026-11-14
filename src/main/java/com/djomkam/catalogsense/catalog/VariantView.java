package com.djomkam.catalogsense.catalog;

import java.math.BigDecimal;

/** A stored variant as returned by the API. Unlike the request payload, {@code inStock} is always set. */
public record VariantView(String sku, String color, String size, BigDecimal price, boolean inStock) {

    public static VariantView from(Variant variant) {
        return new VariantView(variant.getSku(), variant.getColor(), variant.getSize(), variant.getPrice(),
                variant.isInStock());
    }
}
