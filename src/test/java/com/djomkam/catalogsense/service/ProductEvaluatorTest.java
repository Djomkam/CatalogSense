package com.djomkam.catalogsense.service;

import com.djomkam.catalogsense.TestProducts;
import com.djomkam.catalogsense.api.ProductPayload;
import com.djomkam.catalogsense.api.VariantPayload;
import com.djomkam.catalogsense.domain.Product;
import com.djomkam.catalogsense.domain.Severity;
import com.djomkam.catalogsense.validation.RequiredFieldsRule;
import com.djomkam.catalogsense.validation.ValidationRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

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
                nullSku, CATALOG, Map.of(), Map.of(), rules, NOW, new HashSet<>()
        );
        ProductOutcome outcomeBlank = ProductEvaluator.evaluate(
                blankSku, CATALOG, Map.of(), Map.of(), rules, NOW, new HashSet<>()
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
    @DisplayName("Duplicate SKU in batch returns Rejected; only first occurrence records DUPLICATE_SKU health issue")
    void duplicateSkuInBatch() {
        ProductPayload p1 = TestProducts.valid("SKU-DUP", 1);
        ProductPayload p2 = TestProducts.valid("SKU-DUP", 2);

        Map<String, Long> freq = Map.of("SKU-DUP", 2L);
        Set<String> firstSeen = new HashSet<>();

        ProductOutcome out1 = ProductEvaluator.evaluate(p1, CATALOG, freq, Map.of(), rules, NOW, firstSeen);
        ProductOutcome out2 = ProductEvaluator.evaluate(p2, CATALOG, freq, Map.of(), rules, NOW, firstSeen);

        assertThat(out1).isInstanceOf(ProductOutcome.Rejected.class);
        ProductOutcome.Rejected r1 = (ProductOutcome.Rejected) out1;
        assertThat(r1.issuesToRecord()).hasSize(1);
        assertThat(r1.issuesToRecord().getFirst().getRule()).isEqualTo(ProductEvaluator.DUPLICATE_SKU);
        assertThat(r1.issuesToRecord().getFirst().getSeverity()).isEqualTo(Severity.ERROR);

        assertThat(out2).isInstanceOf(ProductOutcome.Rejected.class);
        ProductOutcome.Rejected r2 = (ProductOutcome.Rejected) out2;
        assertThat(r2.issuesToRecord()).isEmpty();
    }

    @Test
    @DisplayName("Stale or identical version returns Stale outcome")
    void staleVersion() {
        Product existing = new Product(CATALOG, "SKU-STALE");
        existing.setVersion(5L);

        ProductPayload stalePayload = TestProducts.withPrice("SKU-STALE", 4L, BigDecimal.TEN);
        ProductPayload samePayload = TestProducts.withPrice("SKU-STALE", 5L, BigDecimal.TEN);

        ProductOutcome outStale = ProductEvaluator.evaluate(
                stalePayload, CATALOG, Map.of("SKU-STALE", 1L), Map.of("SKU-STALE", existing), rules, NOW, new HashSet<>()
        );
        ProductOutcome outSame = ProductEvaluator.evaluate(
                samePayload, CATALOG, Map.of("SKU-STALE", 1L), Map.of("SKU-STALE", existing), rules, NOW, new HashSet<>()
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
                invalid, CATALOG, Map.of("SKU-ERR", 1L), Map.of(), rules, NOW, new HashSet<>()
        );

        assertThat(outcome).isInstanceOf(ProductOutcome.Rejected.class);
        ProductOutcome.Rejected rejected = (ProductOutcome.Rejected) outcome;
        assertThat(rejected.sku()).isEqualTo("SKU-ERR");
        assertThat(rejected.reasons()).contains("Product name is missing. Add a title so shoppers can find it.");
        assertThat(rejected.issuesToRecord()).hasSize(1);
    }

    @Test
    @DisplayName("Valid product with variants returns Accepted outcome with updated product entity")
    void validProductAccepted() {
        ProductPayload valid = new ProductPayload(
                "SKU-OK", "Running Shoe", "Nike", "Shoes", BigDecimal.valueOf(120), "USD",
                "https://example.com/shoe.jpg", 1L,
                List.of(new VariantPayload("SKU-OK-RED", "Red", "10", BigDecimal.valueOf(125), true),
                        new VariantPayload("SKU-OK-BLU", "Blue", "10", BigDecimal.valueOf(125), false))
        );

        ProductOutcome outcome = ProductEvaluator.evaluate(
                valid, CATALOG, Map.of("SKU-OK", 1L), Map.of(), rules, NOW, new HashSet<>()
        );

        assertThat(outcome).isInstanceOf(ProductOutcome.Accepted.class);
        ProductOutcome.Accepted accepted = (ProductOutcome.Accepted) outcome;
        assertThat(accepted.product().getSku()).isEqualTo("SKU-OK");
        assertThat(accepted.product().getName()).isEqualTo("Running Shoe");
        assertThat(accepted.product().getVariants()).hasSize(2);
        assertThat(accepted.product().isInStock()).isTrue();
        assertThat(accepted.hasWarnings()).isFalse();
    }
}
