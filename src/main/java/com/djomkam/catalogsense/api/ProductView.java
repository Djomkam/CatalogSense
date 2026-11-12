package com.djomkam.catalogsense.api;

import com.djomkam.catalogsense.domain.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductView(String sku, String name, String brand, String category, BigDecimal price,
                          String currency, String imageUrl, long version, Instant updatedAt,
                          List<VariantPayload> variants) {

    public static ProductView from(Product p) {
        return new ProductView(p.getSku(), p.getName(), p.getBrand(), p.getCategory(), p.getPrice(),
                p.getCurrency(), p.getImageUrl(), p.getVersion(), p.getUpdatedAt(),
                p.getVariants().stream()
                        .map(v -> new VariantPayload(v.getSku(), v.getColor(), v.getSize(), v.getPrice(), v.isInStock()))
                        .toList());
    }
}
