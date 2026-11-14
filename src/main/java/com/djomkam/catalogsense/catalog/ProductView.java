package com.djomkam.catalogsense.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A stored product as returned by the API. */
public record ProductView(String sku, String name, String brand, String category, BigDecimal price,
                          String currency, String imageUrl, long version, Instant updatedAt,
                          List<VariantView> variants) {

    public static ProductView from(Product product) {
        return new ProductView(product.getSku(), product.getName(), product.getBrand(), product.getCategory(),
                product.getPrice(), product.getCurrency(), product.getImageUrl(), product.getVersion(),
                product.getUpdatedAt(), product.getVariants().stream().map(VariantView::from).toList());
    }
}
