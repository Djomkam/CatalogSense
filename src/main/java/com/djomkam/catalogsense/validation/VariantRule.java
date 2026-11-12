package com.djomkam.catalogsense.validation;

import com.djomkam.catalogsense.api.ProductPayload;
import com.djomkam.catalogsense.api.VariantPayload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class VariantRule implements ValidationRule {

    @Override
    public String name() {
        return "VARIANTS";
    }

    @Override
    public List<Violation> validate(ProductPayload p) {
        List<Violation> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean anyInStock = false;
        for (VariantPayload v : p.variantsOrEmpty()) {
            if (v.sku() == null || v.sku().isBlank()) {
                out.add(Violation.error(name(), "variants.sku", "A variant has no SKU."));
                continue;
            }
            if (!seen.add(v.sku())) {
                out.add(Violation.error(name(), "variants[" + v.sku() + "].sku", "Variant SKU '" + v.sku() + "' appears twice."));
            }
            if (v.inStock() == null || v.inStock()) {
                anyInStock = true;
            }
        }
        if (!p.variantsOrEmpty().isEmpty() && !anyInStock) {
            out.add(Violation.warning(name(), "variants", "All variants are out of stock. The product will rank lower."));
        }
        return out;
    }
}
