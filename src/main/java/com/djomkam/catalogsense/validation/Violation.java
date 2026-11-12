package com.djomkam.catalogsense.validation;

import com.djomkam.catalogsense.domain.Severity;

public record Violation(String rule, Severity severity, String field, String message) {

    public static Violation error(String rule, String field, String message) {
        return new Violation(rule, Severity.ERROR, field, message);
    }

    public static Violation warning(String rule, String field, String message) {
        return new Violation(rule, Severity.WARNING, field, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }
}
