package com.djomkam.catalogsense.api;

import java.math.BigDecimal;

public record VariantPayload(String sku, String color, String size, BigDecimal price, Boolean inStock) {
}
