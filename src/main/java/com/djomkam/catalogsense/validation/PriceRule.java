package com.djomkam.catalogsense.validation;

import com.djomkam.catalogsense.api.ProductPayload;
import com.djomkam.catalogsense.api.VariantPayload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

@Component
public class PriceRule implements ValidationRule {

    @Override
    public String name() {
        return "PRICE";
    }

    @Override
    public List<Violation> validate(ProductPayload p) {
        List<Violation> violationList = new ArrayList<>();
        if (p.price() == null) {
            violationList.add(Violation.error(name(), "price", "Price is missing. Products without a price can't be sold."));
        } else if (p.price().signum() <= 0) {
            violationList.add(Violation.error(name(), "price", "Price must be greater than 0 (got " + p.price().toPlainString() + ")."));
        }
        if (p.currency() == null || !isIsoCurrency(p.currency())) {
            violationList.add(Violation.error(name(), "currency",
                    "Currency '" + p.currency() + "' is not a valid ISO 4217 code such as CAD or USD."));
        }
        for (VariantPayload v : p.variantsOrEmpty()) {
            if (v.price() != null && v.price().signum() <= 0) {
                violationList.add(Violation.error(name(), "variants[" + v.sku() + "].price",
                        "Variant price must be greater than 0 (got " + v.price().toPlainString() + ")."));
            }
            if (v.price() != null && p.price() != null && p.price().signum() > 0
                    && v.price().compareTo(p.price().multiply(BigDecimal.TEN)) > 0) {
                violationList.add(Violation.warning(name(), "variants[" + v.sku() + "].price",
                        "Variant price is more than 10x the product price. Check for a typo."));
            }
        }
        return violationList;
    }

    private static boolean isIsoCurrency(String code) {
        try {
            Currency.getInstance(code.trim().toUpperCase(java.util.Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
