package com.djomkam.catalogsense.health;

import java.util.List;

/**
 * Data Health overview for one catalog.
 *
 * @param productCount products stored in the catalog
 * @param skusTracked  stored SKUs plus SKUs that were rejected and never stored
 * @param score        0-100, share of tracked SKUs with no open issue (see {@link HealthScore})
 */
public record HealthSummary(
        String catalogId,
        long productCount,
        long skusTracked,
        long skusWithErrors,
        long skusWithWarnings,
        int score,
        List<RuleIssueCount> issuesByRule) {
}
