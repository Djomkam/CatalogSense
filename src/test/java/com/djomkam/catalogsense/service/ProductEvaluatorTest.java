package com.djomkam.catalogsense.service;

import com.djomkam.catalogsense.TestProducts;
import com.djomkam.catalogsense.api.ProductPayload;
import com.djomkam.catalogsense.api.VariantPayload;
import com.djomkam.catalogsense.domain.Severity;
import com.djomkam.catalogsense.validation.RequiredFieldsRule;
import com.djomkam.catalogsense.validation.ValidationRule;
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
    private final List<ValidationRule> rules = List.of(new RequiredFieldsRule());

    @Test
    @DisplayName("Missing or blank SKU returns Rejected outcome without recording DB issue")
    void missingSkuRejected() {
        ProductPayload nullSku = new ProductPayload(null, "Shoes", "Nike", "Apparel", BigDecimal.TEN, "USD", null, 1L, null);
        ProductPayload blankSku = new ProductPayload("   ", "Shoes", "Nike", "Apparel", BigDecimal.TEN, "USD", null, 1L, null);

        ProductOutcome outcomeNull = ProductEvaluator.evaluate(
                nullSku, CATALOG, Map.of(), Map.of(), rules, NOW
        );
        ProductOutcome outcomeBlank = ProductEvaluator.evaluate(
                blankSku, CATALOG, Map.of(), Map.of(), rules, NOW
        );

        assertThat(outcomeNull).isInstanceOf(ProductOutcome.Rejected.class);
        ProductOutcome.Rejected rejectedNull = (ProductOutcome.Rejected) outcomeNull;
        assertThat(rejectedNull.sku()).isEqualTo("(missing)");
        assertThat(rejectedNull.issuesToRecord()).isEmpty();

        assertThat(outcomeBlank).isInstanceOf(ProductOutcome.Rejected.class);
        ProductOutcome.Rejected rejectedBlank = (ProductOutcome.Rejected) outcomeBlank;
        assertThat(rejectedBlank.sku()).isEqualTo("(missing)");
        assertThat(rejectedBlank.issuesToRecord()).isEmpty();
    }

    @Test
    @DisplayName("Duplicate SKU in batch is rejected with a DUPLICATE_SKU issue, and evaluation is deterministic")
    void duplicateSkuInBatch() {
        ProductPayload p1 = TestProducts.valid("SKU-DUP", 1);
        ProductPayload p2 = TestProducts.valid("SKU-DUP", 2);
        Map<String, Long> duplicates = Map.of("SKU-DUP", 2L);

        ProductOutcome out1 = ProductEvaluator.evaluate(p1, CATALOG, duplicates, Map.of(), rules, NOW);
        ProductOutcome out2 = ProductEvaluator.evaluate(p2, CATALOG, duplicates, Map.of(), rules, NOW);

        for (ProductOutcome out : List.of(out1, out2)) {
            assertThat(out).isInstanceOf(ProductOutcome.Rejected.class);
            ProductOutcome.Rejected r = (ProductOutcome.Rejected) out;
            assertThat(r.reasons()).containsExactly("SKU appears 2 times in this batch; send it once.");
            assertThat(r.issuesToRecord()).singleElement().satisfies(i -> {
                assertThat(i.getRule()).isEqualTo(ProductEvaluator.DUPLICATE_SKU);
                assertThat(i.getSeverity()).isEqualTo(Severity.ERROR);
            });
        }
        // Same inputs, same result: no hidden state between calls.
        assertThat(ProductEvaluator.evaluate(p1, CATALOG, duplicates, Map.of(), rules, NOW))
                .usingRecursiveComparison().isEqualTo(out1);
    }

    @Test
    @DisplayName("Stale or identical version returns Stale outcome")
    void staleVersion() {
        Map<String, Long> stored = Map.of("SKU-STALE", 5L);

        ProductPayload stalePayload = TestProducts.withPrice("SKU-STALE", 4L, BigDecimal.TEN);
        ProductPayload samePayload = TestProducts.withPrice("SKU-STALE", 5L, BigDecimal.TEN);

        ProductOutcome outStale = ProductEvaluator.evaluate(
                stalePayload, CATALOG, Map.of(), stored, rules, NOW
        );
        ProductOutcome outSame = ProductEvaluator.evaluate(
                samePayload, CATALOG, Map.of(), stored, rules, NOW
        );

        assertThat(outStale).isInstanceOf(ProductOutcome.Stale.class);
        assertThat(((ProductOutcome.Stale) outStale).sku()).isEqualTo("SKU-STALE");

        assertThat(outSame).isInstanceOf(ProductOutcome.Stale.class);
        assertThat(((ProductOutcome.Stale) outSame).sku()).isEqualTo("SKU-STALE");
    }

    @Test
    @DisplayName("Validation rule error results in Rejected outcome with health issues")
    void validationErrorRejected() {
        // Missing name violates RequiredFieldsRule with ERROR severity
        ProductPayload invalid = new ProductPayload("SKU-ERR", null, "Nike", "Apparel", BigDecimal.TEN, "USD", null, 1L, null);

        ProductOutcome outcome = ProductEvaluator.evaluate(
                invalid, CATALOG, Map.of(), Map.of(), rules, NOW
        );

        assertThat(outcome).isInstanceOf(ProductOutcome.Rejected.class);
        ProductOutcome.Rejected rejected = (ProductOutcome.Rejected) outcome;
        assertThat(rejected.sku()).isEqualTo("SKU-ERR");
        assertThat(rejected.reasons()).contains("Product name is missing. Add a title so shoppers can find it.");
        assertThat(rejected.issuesToRecord()).hasSize(1);
    }

    @Test
    @DisplayName("Valid product returns Accepted carrying the payload; no entity is created or changed")
    void validProductAccepted() {
        ProductPayload valid = new ProductPayload(
                "SKU-OK", "Running Shoe", "Nike", "Shoes", BigDecimal.valueOf(120), "USD",
                "https://example.com/shoe.jpg", 1L,
                List.of(new VariantPayload("SKU-OK-RED", "Red", "10", BigDecimal.valueOf(125), true),
                        new VariantPayload("SKU-OK-BLU", "Blue", "10", BigDecimal.valueOf(125), false))
        );

        ProductOutcome outcome = ProductEvaluator.evaluate(
                valid, CATALOG, Map.of(), Map.of(), rules, NOW
        );

        assertThat(outcome).isInstanceOf(ProductOutcome.Accepted.class);
        ProductOutcome.Accepted accepted = (ProductOutcome.Accepted) outcome;
        assertThat(accepted.sku()).isEqualTo("SKU-OK");
        assertThat(accepted.payload()).isSameAs(valid);
        assertThat(accepted.issues()).isEmpty();
        assertThat(accepted.hasWarnings()).isFalse();
    }
}
