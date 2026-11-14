package com.djomkam.catalogsense.ingestion;

import java.math.BigDecimal;

/** A variant as sent by the merchant. {@code inStock} may be omitted, which means in stock. */
public record VariantPayload(String sku, String color, String size, BigDecimal price, Boolean inStock) {

    public boolean isInStockOrDefault() {
        return inStock == null || inStock;
    }

    VariantPayload normalized() {
        return new VariantPayload(sku, color, size, price, isInStockOrDefault());
    }
}
