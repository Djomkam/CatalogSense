package com.djomkam.catalogsense.ingestion.validation;

import com.djomkam.catalogsense.health.Severity;

/**
 * One problem found in a product payload.
 *
 * @param field   the payload field, e.g. "price" or "variants[TR-9].price" (see {@link #variantField})
 * @param message written for the merchant: what is wrong and how to fix it
 */
public record Violation(RuleId rule, Severity severity, String field, String message) {

    public static Violation error(RuleId rule, String field, String message) {
        return new Violation(rule, Severity.ERROR, field, message);
    }

    public static Violation warning(RuleId rule, String field, String message) {
        return new Violation(rule, Severity.WARNING, field, message);
    }

    /** Field path of a variant's field: {@code variants[<variantSku>].<field>}. */
    public static String variantField(String variantSku, String field) {
        return "variants[" + variantSku + "]." + field;
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
