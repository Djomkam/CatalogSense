package com.djomkam.catalogsense.api;

import java.util.List;

/**
 * Data Health overview for one catalog.
 *
 * @param score 0-100, share of tracked SKUs with no open issue
 */
public record HealthSummary(
        String catalogId,
        long productsIndexed,
        long skusTracked,
        long skusWithErrors,
        long skusWithWarnings,
        int score,
        List<RuleCount> issuesByRule) {

    public record RuleCount(String rule, String severity, long count) {
    }
}
