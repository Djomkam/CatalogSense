package com.djomkam.catalogsense.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registers the annotated bean only when search is on ({@code catalogsense.search.enabled}, default true).
 * With search off, the app still ingests catalogs and publishes events; it just doesn't talk to OpenSearch.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@ConditionalOnProperty(name = "catalogsense.search.enabled", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnSearchEnabled {
}
