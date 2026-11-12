package com.djomkam.catalogsense;

import com.djomkam.catalogsense.api.ProductPayload;
import com.djomkam.catalogsense.api.VariantPayload;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.List;

@UtilityClass
public class TestProducts {

    public static ProductPayload valid(String sku, long version) {
        return new ProductPayload(sku, "Trail Runner " + sku, "Northpeak", "Shoes",
                new BigDecimal("129.99"), "CAD", "https://cdn.example.com/" + sku + ".jpg", version,
                List.of(new VariantPayload(sku + "-9", "Black", "9", null, true)));
    }

    public static ProductPayload withPrice(String sku, long version, BigDecimal price) {
        ProductPayload p = valid(sku, version);
        return new ProductPayload(p.sku(), p.name(), p.brand(), p.category(), price, p.currency(),
                p.imageUrl(), p.version(), p.variants());
    }

    public static ProductPayload withoutImage(String sku, long version) {
        ProductPayload p = valid(sku, version);
        return new ProductPayload(p.sku(), p.name(), p.brand(), p.category(), p.price(), p.currency(),
                null, p.version(), p.variants());
    }
}
