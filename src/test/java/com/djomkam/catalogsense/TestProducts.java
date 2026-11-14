package com.djomkam.catalogsense;

import com.djomkam.catalogsense.ingestion.ProductPayload;
import com.djomkam.catalogsense.ingestion.VariantPayload;

import java.math.BigDecimal;
import java.util.List;

/** Sample payloads for tests: a valid product, and variations of it with one thing wrong. */
public final class TestProducts {

    private TestProducts() {
    }

    public static ProductPayload valid(String sku, long version) {
        return new ProductPayload(sku, "Trail Runner " + sku, "Northpeak", "Shoes",
                new BigDecimal("129.99"), "CAD", "https://cdn.example.com/" + sku + ".jpg", version,
                List.of(new VariantPayload(sku + "-9", "Black", "9", null, true)));
    }

    public static ProductPayload withPrice(String sku, long version, BigDecimal price) {
        ProductPayload base = valid(sku, version);
        return new ProductPayload(base.sku(), base.name(), base.brand(), base.category(), price, base.currency(),
                base.imageUrl(), base.version(), base.variants());
    }

    public static ProductPayload withoutImage(String sku, long version) {
        return withImageUrl(sku, version, null);
    }

    public static ProductPayload withImageUrl(String sku, long version, String imageUrl) {
        ProductPayload base = valid(sku, version);
        return new ProductPayload(base.sku(), base.name(), base.brand(), base.category(), base.price(), base.currency(),
                imageUrl, base.version(), base.variants());
    }

    public static ProductPayload withCurrency(String sku, long version, String currency) {
        ProductPayload base = valid(sku, version);
        return new ProductPayload(base.sku(), base.name(), base.brand(), base.category(), base.price(), currency,
                base.imageUrl(), base.version(), base.variants());
    }

    public static ProductPayload withVariants(String sku, long version, List<VariantPayload> variants) {
        ProductPayload base = valid(sku, version);
        return new ProductPayload(base.sku(), base.name(), base.brand(), base.category(), base.price(), base.currency(),
                base.imageUrl(), base.version(), variants);
    }
}
