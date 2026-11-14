package com.djomkam.catalogsense.ingestion.validation;

import com.djomkam.catalogsense.ingestion.ProductPayload;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(1)
public class RequiredFieldsRule implements ValidationRule {

    @Override
    public RuleId id() {
        return RuleId.REQUIRED_FIELDS;
    }

    @Override
    public List<Violation> validate(ProductPayload product) {
        List<Violation> violations = new ArrayList<>();
        if (isBlank(product.name())) {
            violations.add(error("name", "Product name is missing. Add a title so shoppers can find it."));
        }
        if (product.version() == null) {
            violations.add(error("version",
                    "Version is missing. Send a number that grows with each change (e.g. the source updatedAt in epoch millis)."));
        }
        if (isBlank(product.category())) {
            violations.add(warning("category", "Category is missing. The product won't appear in category facets."));
        }
        if (isBlank(product.brand())) {
            violations.add(warning("brand", "Brand is missing. The product won't appear in the brand facet."));
        }
        return violations;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
