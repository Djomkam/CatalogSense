package com.djomkam.catalogsense.ingestion.validation;

import com.djomkam.catalogsense.ingestion.ProductPayload;

import java.util.List;

/**
 * One Data Health check (strategy pattern). Add a rule by creating a new {@code @Component} that
 * implements this interface; give it an {@code @Order} so violations are always reported in the
 * same order. Rules receive the payload already normalized (trimmed name, uppercase currency).
 * Messages are written for the merchant: say what is wrong and how to fix it.
 */
public interface ValidationRule {

    RuleId id();

    List<Violation> validate(ProductPayload product);

    default Violation error(String field, String message) {
        return Violation.error(id(), field, message);
    }

    default Violation warning(String field, String message) {
        return Violation.warning(id(), field, message);
    }
}
