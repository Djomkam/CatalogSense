package com.djomkam.catalogsense.validation;

import com.djomkam.catalogsense.api.ProductPayload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RequiredFieldsRule implements ValidationRule {

    @Override
    public String name() {
        return "REQUIRED_FIELDS";
    }

    @Override
    public List<Violation> validate(ProductPayload p) {
        List<Violation> out = new ArrayList<>();
        if (isBlank(p.name())) {
            out.add(Violation.error(name(), "name", "Product name is missing. Add a title so shoppers can find it."));
        }
        if (p.version() == null) {
            out.add(Violation.error(name(), "version",
                    "Version is missing. Send a number that grows with each change (e.g. the source updatedAt in epoch millis)."));
        }
        if (isBlank(p.category())) {
            out.add(Violation.warning(name(), "category", "Category is missing. The product won't appear in category facets."));
        }
        if (isBlank(p.brand())) {
            out.add(Violation.warning(name(), "brand", "Brand is missing. The product won't appear in the brand facet."));
        }
        return out;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
