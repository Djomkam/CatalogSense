package com.djomkam.catalogsense.ingestion;

import com.djomkam.catalogsense.TestProducts;
import com.djomkam.catalogsense.health.Severity;
import com.djomkam.catalogsense.ingestion.validation.RequiredFieldsRule;
import com.djomkam.catalogsense.ingestion.validation.RuleId;
import com.djomkam.catalogsense.ingestion.validation.ValidationRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductEvaluatorTest {

    private static final String CATALOG = "cat-1";
    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    private static final BatchContext EMPTY_CATALOG = new BatchContext(CATALOG, Map.of(), Map.of(), NOW);

    private final List<ValidationRule> rules = List.of(new RequiredFieldsRule());

    private ProductOutcome evaluate(ProductPayload payload, BatchContext context) {
        return ProductEvaluator.evaluate(payload, context, rules);
    }

    @Test
    @DisplayName("Missing or blank SKU is rejected, with nothing to record as a health issue")
    void missingSkuRejected() {
        ProductPayload nullSku = new ProductPayload(null, "Shoes", "Nike", "Apparel", BigDecimal.TEN, "USD", null, 1L, null);
        ProductPayload blankSku = new ProductPayload("   ", "Shoes", "Nike", "Apparel", BigDecimal.TEN, "USD", null, 1L, null);

        for (ProductPayload payload : List.of(nullSku, blankSku)) {
            assertThat(evaluate(payload, EMPTY_CATALOG)).isInstanceOfSatisfying(ProductOutcome.Rejected.class, rejected -> {
                assertThat(rejected.sku()).isEqualTo(ProductEvaluator.MISSING_SKU_PLACEHOLDER);
                assertThat(rejected.violationsToRecord()).isEmpty();
            });
        }
    }

    @Test
    @DisplayName("Duplicate SKU in batch is rejected with a DUPLICATE_SKU violation, and evaluation is deterministic")
    void duplicateSkuInBatch() {
        ProductPayload firstCopy = TestProducts.valid("SKU-DUP", 1);
        ProductPayload secondCopy = TestProducts.valid("SKU-DUP", 2);
        BatchContext withDuplicate = BatchContext.of(CATALOG, Map.of("SKU-DUP", 2L), Map.of(), NOW);

        for (ProductPayload payload : List.of(firstCopy, secondCopy)) {
            assertThat(evaluate(payload, withDuplicate)).isInstanceOfSatisfying(ProductOutcome.Rejected.class, rejected -> {
                assertThat(rejected.reasons()).containsExactly("SKU appears 2 times in this batch; send it once.");
                assertThat(rejected.violationsToRecord()).singleElement().satisfies(violation -> {
                    assertThat(violation.rule()).isEqualTo(RuleId.DUPLICATE_SKU);
                    assertThat(violation.severity()).isEqualTo(Severity.ERROR);
                });
            });
        }
        // Same inputs, same result: no hidden state between calls.
        assertThat(evaluate(firstCopy, withDuplicate)).isEqualTo(evaluate(firstCopy, withDuplicate));
    }

    @Test
    @DisplayName("Older or identical version is Stale")
    void staleVersion() {
        BatchContext storedAtVersion5 = new BatchContext(CATALOG, Map.of(), Map.of("SKU-STALE", 5L), NOW);

        for (long version : List.of(4L, 5L)) {
            assertThat(evaluate(TestProducts.withPrice("SKU-STALE", version, BigDecimal.TEN), storedAtVersion5))
                    .isEqualTo(new ProductOutcome.Stale("SKU-STALE"));
        }
    }

    @Test
    @DisplayName("A rule error rejects the product and keeps its violations to record")
    void validationErrorRejected() {
        ProductPayload withoutName = new ProductPayload("SKU-ERR", null, "Nike", "Apparel", BigDecimal.TEN, "USD", null, 1L, null);

        assertThat(evaluate(withoutName, EMPTY_CATALOG)).isInstanceOfSatisfying(ProductOutcome.Rejected.class, rejected -> {
            assertThat(rejected.sku()).isEqualTo("SKU-ERR");
            assertThat(rejected.reasons()).contains("Product name is missing. Add a title so shoppers can find it.");
            assertThat(rejected.violationsToRecord()).singleElement()
                    .satisfies(violation -> assertThat(violation.rule()).isEqualTo(RuleId.REQUIRED_FIELDS));
        });
    }

    @Test
    @DisplayName("A valid product is Accepted with its payload; warnings make hasWarnings() true")
    void validProductAccepted() {
        ProductPayload valid = TestProducts.valid("SKU-OK", 1);
        ProductPayload withoutCategory = new ProductPayload("SKU-W", "Shoe", "Nike", null, BigDecimal.TEN, "USD",
                null, 1L, null);

        assertThat(evaluate(valid, EMPTY_CATALOG)).isInstanceOfSatisfying(ProductOutcome.Accepted.class, accepted -> {
            assertThat(accepted.sku()).isEqualTo("SKU-OK");
            assertThat(accepted.payload()).isSameAs(valid);
            assertThat(accepted.hasWarnings()).isFalse();
        });
        assertThat(evaluate(withoutCategory, EMPTY_CATALOG)).isInstanceOfSatisfying(ProductOutcome.Accepted.class,
                accepted -> assertThat(accepted.hasWarnings()).isTrue());
    }
}
