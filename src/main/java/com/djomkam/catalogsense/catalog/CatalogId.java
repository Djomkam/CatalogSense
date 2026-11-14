package com.djomkam.catalogsense.catalog;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A valid catalog id: 1 to 64 letters, digits, '-' or '_'. Used on every {@code {catalogId}} path
 * variable, so the format is defined once. A bad id is a 400.
 */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = CatalogId.PATTERN)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface CatalogId {

    String PATTERN = "[A-Za-z0-9_-]{1,64}";

    String message() default "must match " + PATTERN;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
