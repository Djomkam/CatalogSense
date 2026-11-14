package com.djomkam.catalogsense.ingestion.validation;

import com.djomkam.catalogsense.ingestion.ProductPayload;
import com.djomkam.catalogsense.ingestion.VariantPayload;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

@Component
@Order(2)
public class PriceRule implements ValidationRule {

    /** A variant priced above this multiple of the product price is probably a typo. */
    static final BigDecimal VARIANT_PRICE_OUTLIER_FACTOR = BigDecimal.TEN;

    @Override
    public RuleId id() {
        return RuleId.PRICE;
    }

    @Override
    public List<Violation> validate(ProductPayload product) {
        List<Violation> violations = new ArrayList<>();
        checkProductPrice(product, violations);
        checkCurrency(product, violations);
        checkVariantPrices(product, violations);
        return violations;
    }

    private void checkProductPrice(ProductPayload product, List<Violation> violations) {
        if (product.price() == null) {
            violations.add(error("price", "Price is missing. Products without a price can't be sold."));
        } else if (product.price().signum() <= 0) {
            violations.add(error("price",
                    "Price must be greater than 0 (got " + product.price().toPlainString() + ")."));
        }
    }

    private void checkCurrency(ProductPayload product, List<Violation> violations) {
        if (!isIsoCurrency(product.currency())) {
            violations.add(error("currency",
                    "Currency '" + product.currency() + "' is not a valid ISO 4217 code such as CAD or USD."));
        }
    }

    private void checkVariantPrices(ProductPayload product, List<Violation> violations) {
        boolean productPriceIsValid = product.price() != null && product.price().signum() > 0;
        for (VariantPayload variant : product.variantsOrEmpty()) {
            if (variant.price() == null) {
                continue;
            }
            String field = Violation.variantField(variant.sku(), "price");
            if (variant.price().signum() <= 0) {
                violations.add(error(field,
                        "Variant price must be greater than 0 (got " + variant.price().toPlainString() + ")."));
            }
            if (productPriceIsValid
                    && variant.price().compareTo(product.price().multiply(VARIANT_PRICE_OUTLIER_FACTOR)) > 0) {
                violations.add(warning(field,
                        "Variant price is more than " + VARIANT_PRICE_OUTLIER_FACTOR
                                + "x the product price. Check for a typo."));
            }
        }
    }

    /** The payload is normalized before validation, so the code is already trimmed and uppercase. */
    private static boolean isIsoCurrency(String code) {
        if (code == null) {
            return false;
        }
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
