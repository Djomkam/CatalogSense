package com.djomkam.catalogsense.service;

import com.djomkam.catalogsense.api.ProductPayload;
import com.djomkam.catalogsense.domain.HealthIssue;

import java.util.List;

/**
 * Algebraic data type representing the decision taken for one product payload.
 * Outcomes are plain values: they never hold JPA entities, so evaluating a batch
 * has no effect on the persistence context.
 */
public sealed interface ProductOutcome {

    /** The payload is valid and newer than what is stored; it must be applied. */
    record Accepted(
            ProductPayload payload,
            List<HealthIssue> issues,
            boolean hasWarnings
    ) implements ProductOutcome {
        public String sku() {
            return payload.sku();
        }
    }

    record Rejected(
            String sku,
            List<String> reasons,
            List<HealthIssue> issuesToRecord
    ) implements ProductOutcome {}

    record Stale(
            String sku
    ) implements ProductOutcome {}
}
