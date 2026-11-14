package com.djomkam.catalogsense.ingestion;

import com.djomkam.catalogsense.ingestion.validation.Violation;

import java.util.List;

/**
 * The decision taken for one product payload (a sealed type, so every {@code switch} over it must
 * handle all three cases). Outcomes are plain values: they hold payloads and {@link Violation}s,
 * never JPA entities, so evaluating a batch has no effect on the persistence context.
 * {@link IngestionService} turns the violations into health issue rows.
 */
public sealed interface ProductOutcome {

    /** The payload is valid and newer than what is stored; it must be applied. */
    record Accepted(ProductPayload payload, List<Violation> warnings) implements ProductOutcome {

        public String sku() {
            return payload.sku();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    /**
     * @param reasons            messages for the ingestion report
     * @param violationsToRecord what to store as health issues (empty when the SKU itself is missing)
     */
    record Rejected(String sku, List<String> reasons, List<Violation> violationsToRecord) implements ProductOutcome {
    }

    /** The stored version is the same or newer: nothing changes, not even the health issues. */
    record Stale(String sku) implements ProductOutcome {
    }
}
