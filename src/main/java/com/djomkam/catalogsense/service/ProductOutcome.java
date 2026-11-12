package com.djomkam.catalogsense.service;

import com.djomkam.catalogsense.domain.HealthIssue;
import com.djomkam.catalogsense.domain.Product;

import java.util.List;

/**
 * Algebraic data type representing the possible outcomes of evaluating a product payload.
 */
public sealed interface ProductOutcome {

    record Accepted(
            Product product,
            List<HealthIssue> issues,
            boolean hasWarnings
    ) implements ProductOutcome {}

    record Rejected(
            String sku,
            List<String> reasons,
            List<HealthIssue> issuesToRecord
    ) implements ProductOutcome {}

    record Stale(
            String sku
    ) implements ProductOutcome {}
}
