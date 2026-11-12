package com.djomkam.catalogsense.validation;

import com.djomkam.catalogsense.api.ProductPayload;

import java.util.List;

/**
 * One Data Health check. Add a rule by creating a new @Component that implements this interface.
 * Messages are written for the merchant: say what is wrong and how to fix it.
 */
public interface ValidationRule {

    String name();

    List<Violation> validate(ProductPayload product);
}
