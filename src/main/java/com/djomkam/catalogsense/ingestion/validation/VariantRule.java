package com.djomkam.catalogsense.ingestion.validation;

import com.djomkam.catalogsense.ingestion.ProductPayload;
import com.djomkam.catalogsense.ingestion.VariantPayload;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Order(3)
public class VariantRule implements ValidationRule {

    @Override
    public RuleId id() {
        return RuleId.VARIANTS;
    }

    @Override
    public List<Violation> validate(ProductPayload product) {
        List<Violation> violations = new ArrayList<>();
        Set<String> seenVariantSkus = new HashSet<>();
        boolean anyVariantInStock = false;
        for (VariantPayload variant : product.variantsOrEmpty()) {
            if (variant.sku() == null || variant.sku().isBlank()) {
                violations.add(error("variants.sku", "A variant has no SKU."));
                continue;
            }
            if (!seenVariantSkus.add(variant.sku())) {
                violations.add(error(Violation.variantField(variant.sku(), "sku"),
                        "Variant SKU '" + variant.sku() + "' appears twice."));
            }
            anyVariantInStock |= variant.isInStockOrDefault();
        }
        if (!product.variantsOrEmpty().isEmpty() && !anyVariantInStock) {
            violations.add(warning("variants", "All variants are out of stock. The product will rank lower."));
        }
        return violations;
    }
}
