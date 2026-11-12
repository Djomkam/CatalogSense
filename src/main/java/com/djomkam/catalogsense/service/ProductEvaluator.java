package com.djomkam.catalogsense.service;

import com.djomkam.catalogsense.api.ProductPayload;
import com.djomkam.catalogsense.domain.HealthIssue;
import com.djomkam.catalogsense.domain.Product;
import com.djomkam.catalogsense.domain.Severity;
import com.djomkam.catalogsense.validation.ValidationRule;
import com.djomkam.catalogsense.validation.Violation;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure, side-effect-free evaluator for individual product payloads against catalog state and rules.
 */
@UtilityClass
public class ProductEvaluator {

    public static final String DUPLICATE_SKU = "DUPLICATE_SKU";

    public static ProductOutcome evaluate(
            ProductPayload payload,
            String catalogId,
            Map<String, Long> skuFrequency,
            Map<String, Product> existingProducts,
            List<ValidationRule> rules,
            Instant now,
            Set<String> firstSeenDuplicates
    ) {
        String sku = payload.sku();

        // 1. Missing SKU check
        if (sku == null || sku.isBlank()) {
            return new ProductOutcome.Rejected(
                    "(missing)",
                    List.of("SKU is missing, so the product can't be identified."),
                    List.of()
            );
        }

        // 2. Batch duplicate SKU check
        if (skuFrequency.getOrDefault(sku, 0L) > 1) {
            String reason = "SKU appears " + skuFrequency.get(sku) + " times in this batch; send it once.";
            List<HealthIssue> issuesToRecord = firstSeenDuplicates.add(sku)
                    ? List.of(new HealthIssue(catalogId, sku, DUPLICATE_SKU, Severity.ERROR, "sku", reason, now))
                    : List.of();

            return new ProductOutcome.Rejected(sku, List.of(reason), issuesToRecord);
        }

        // 3. Stale version check
        Product existing = existingProducts.get(sku);
        if (existing != null && payload.version() != null && payload.version() <= existing.getVersion()) {
            return new ProductOutcome.Stale(sku);
        }

        // 4. Data Health Rules validation
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

        // 5. Accepted
        Product target = existing != null ? existing : new Product(catalogId, sku);
        target.updateFromPayload(payload, now);

        return new ProductOutcome.Accepted(target, healthIssues, !violations.isEmpty());
    }
}
