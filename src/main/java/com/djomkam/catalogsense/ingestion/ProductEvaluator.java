package com.djomkam.catalogsense.ingestion;

import com.djomkam.catalogsense.ingestion.validation.RuleId;
import com.djomkam.catalogsense.ingestion.validation.ValidationRule;
import com.djomkam.catalogsense.ingestion.validation.Violation;

import java.util.List;

/**
 * Pure evaluator: decides what happens to one product payload.
 *
 * <p>It only reads its arguments and returns a {@link ProductOutcome}. It does not mutate
 * shared state or touch JPA entities, so the same inputs always give the same outcome and
 * a batch can be evaluated in any order (or in parallel). Applying the decision is the job
 * of {@link IngestionService}.</p>
 */
public final class ProductEvaluator {

    /** Stands in for the SKU in the report when the payload has none. */
    public static final String MISSING_SKU_PLACEHOLDER = "(missing)";

    private ProductEvaluator() {
    }

    public static ProductOutcome evaluate(ProductPayload payload, BatchContext context, List<ValidationRule> rules) {
        String sku = payload.sku();

        // 1. Missing SKU: nothing to attach an issue to, so it is only reported.
        if (sku == null || sku.isBlank()) {
            return new ProductOutcome.Rejected(MISSING_SKU_PLACEHOLDER,
                    List.of("SKU is missing, so the product can't be identified."), List.of());
        }

        // 2. Same SKU sent more than once in the batch: we can't tell which one is right.
        Long occurrences = context.duplicateSkuCounts().get(sku);
        if (occurrences != null) {
            String reason = "SKU appears " + occurrences + " times in this batch; send it once.";
            return new ProductOutcome.Rejected(sku, List.of(reason),
                    List.of(Violation.error(RuleId.DUPLICATE_SKU, "sku", reason)));
        }

        // 3. Stale version: older or equal to what is stored.
        if (context.isStale(sku, payload.version())) {
            return new ProductOutcome.Stale(sku);
        }

        // 4. Data Health rules.
        List<Violation> violations = rules.stream()
                .flatMap(rule -> rule.validate(payload).stream())
                .toList();
        List<String> errorMessages = violations.stream()
                .filter(Violation::isError)
                .map(Violation::message)
                .toList();
        if (!errorMessages.isEmpty()) {
            return new ProductOutcome.Rejected(sku, errorMessages, violations);
        }

        // 5. Accepted: only warnings are left. The service applies the payload to the entity.
        return new ProductOutcome.Accepted(payload, violations);
    }
}
