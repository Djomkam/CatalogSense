package com.djomkam.catalogsense.ingestion.validation;

/** Identifies the check behind a violation. Stored by name in {@code health_issue.rule}. */
public enum RuleId {
    REQUIRED_FIELDS,
    PRICE,
    VARIANTS,
    IMAGE_URL,
    /** Not a {@link ValidationRule}: raised by the evaluator, which sees the whole batch. */
    DUPLICATE_SKU
}
