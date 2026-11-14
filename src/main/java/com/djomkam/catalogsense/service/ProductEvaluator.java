package com.djomkam.catalogsense.service;

import com.djomkam.catalogsense.api.ProductPayload;
import com.djomkam.catalogsense.domain.HealthIssue;
import com.djomkam.catalogsense.domain.Severity;
import com.djomkam.catalogsense.validation.ValidationRule;
import com.djomkam.catalogsense.validation.Violation;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure evaluator: decides what happens to one product payload.
 *
 * <p>It only reads its arguments and returns a {@link ProductOutcome}. It does not mutate
 * shared state or touch JPA entities, so the same inputs always give the same outcome and
 * a batch can be evaluated in any order (or in parallel). Applying the decision is the job
 * of {@link IngestionService}.</p>
 */
@UtilityClass
public class ProductEvaluator {

    public static final String DUPLICATE_SKU = "DUPLICATE_SKU";

    /**
     * @param duplicateSkus    SKUs that appear more than once in the batch, with their count
     * @param existingVersions version currently stored for each SKU of the batch that already exists
     */
    public static ProductOutcome evaluate(
            ProductPayload payload,
            String catalogId,
            Map<String, Long> duplicateSkus,
            Map<String, Long> existingVersions,
            List<ValidationRule> rules,
            Instant now
    ) {
        String sku = payload.sku();

        // 1. Missing SKU: nothing to attach an issue to, so it is only reported.
        if (sku == null || sku.isBlank()) {
            return new ProductOutcome.Rejected(
                    "(missing)",
                    List.of("SKU is missing, so the product can't be identified."),
                    List.of()
            );
        }

        // 2. Same SKU sent more than once in the batch: we can't tell which one is right.
        Long occurrences = duplicateSkus.get(sku);
        if (occurrences != null) {
            String reason = "SKU appears " + occurrences + " times in this batch; send it once.";
            return new ProductOutcome.Rejected(sku, List.of(reason),
                    List.of(new HealthIssue(catalogId, sku, DUPLICATE_SKU, Severity.ERROR, "sku", reason, now)));
        }

        // 3. Stale version: older or equal to what is stored.
        Long storedVersion = existingVersions.get(sku);
        if (storedVersion != null && payload.version() != null && payload.version() <= storedVersion) {
            return new ProductOutcome.Stale(sku);
        }

        // 4. Data Health rules.
        List<Violation> violations = rules.stream()
                .flatMap(r -> r.validate(payload).stream())
                .toList();

        List<HealthIssue> healthIssues = violations.stream()
                .map(v -> new HealthIssue(catalogId, sku, v.rule(), v.severity(), v.field(), v.message(), now))
                .toList();

        List<String> errors = violations.stream()
                .filter(Violation::isError)
                .map(Violation::message)
                .toList();

        if (!errors.isEmpty()) {
            return new ProductOutcome.Rejected(sku, errors, healthIssues);
        }

        // 5. Accepted: the service applies the payload to the entity.
        return new ProductOutcome.Accepted(payload, healthIssues, !violations.isEmpty());
    }
}
