package com.djomkam.catalogsense.ingestion.validation;

import com.djomkam.catalogsense.TestProducts;
import com.djomkam.catalogsense.health.Severity;
import com.djomkam.catalogsense.ingestion.ProductPayload;
import com.djomkam.catalogsense.ingestion.VariantPayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationRulesTest {

    private final PriceRule priceRule = new PriceRule();
    private final ImageUrlRule imageUrlRule = new ImageUrlRule();
    private final VariantRule variantRule = new VariantRule();
    private final RequiredFieldsRule requiredFieldsRule = new RequiredFieldsRule();

    @Test
    void validProductHasNoViolations() {
        ProductPayload product = TestProducts.valid("SKU-1", 1);

        for (ValidationRule rule : List.of(priceRule, imageUrlRule, variantRule, requiredFieldsRule)) {
            assertThat(rule.validate(product)).as(rule.id().name()).isEmpty();
        }
    }

    @Test
    void zeroPriceIsAnError() {
        List<Violation> violations = priceRule.validate(TestProducts.withPrice("SKU-1", 1, BigDecimal.ZERO));

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.rule()).isEqualTo(RuleId.PRICE);
            assertThat(violation.severity()).isEqualTo(Severity.ERROR);
            assertThat(violation.field()).isEqualTo("price");
        });
    }

    @Test
    void unknownCurrencyIsAnError() {
        assertThat(priceRule.validate(TestProducts.withCurrency("SKU-1", 1, "DOLLARS")))
                .extracting(Violation::field).containsExactly("currency");
    }

    @Test
    void variantPricedFarAboveTheProductIsAWarningOnThatVariant() {
        ProductPayload product = TestProducts.withVariants("SKU-1", 1,
                List.of(new VariantPayload("V1", "Red", "M", new BigDecimal("5000"), true)));

        assertThat(priceRule.validate(product)).singleElement().satisfies(violation -> {
            assertThat(violation.severity()).isEqualTo(Severity.WARNING);
            assertThat(violation.field()).isEqualTo("variants[V1].price");
        });
    }

    @Test
    void missingOrInsecureImageIsAWarning() {
        assertThat(imageUrlRule.validate(TestProducts.withoutImage("SKU-1", 1)))
                .singleElement().extracting(Violation::severity).isEqualTo(Severity.WARNING);
        assertThat(imageUrlRule.validate(TestProducts.withImageUrl("SKU-1", 1, "http://cdn.example.com/a.jpg")))
                .singleElement().extracting(Violation::severity).isEqualTo(Severity.WARNING);
        assertThat(imageUrlRule.validate(TestProducts.withImageUrl("SKU-1", 1, "ftp://files/a.jpg")))
                .hasSize(1);
    }

    @Test
    void duplicateVariantSkuIsAnError() {
        ProductPayload product = TestProducts.withVariants("SKU-1", 1, List.of(
                new VariantPayload("V1", "Red", "M", null, true),
                new VariantPayload("V1", "Red", "L", null, true)));

        assertThat(variantRule.validate(product)).singleElement().satisfies(violation -> {
            assertThat(violation.severity()).isEqualTo(Severity.ERROR);
            assertThat(violation.field()).isEqualTo("variants[V1].sku");
        });
    }

    @Test
    void allVariantsOutOfStockIsAWarning() {
        ProductPayload product = TestProducts.withVariants("SKU-1", 1,
                List.of(new VariantPayload("V1", "Red", "M", null, false)));

        assertThat(variantRule.validate(product)).extracting(Violation::severity).containsExactly(Severity.WARNING);
    }

    @Test
    void missingNameAndVersionAreErrors() {
        ProductPayload product = new ProductPayload("SKU-1", " ", "B", "C", BigDecimal.ONE, "CAD", null, null, null);

        assertThat(requiredFieldsRule.validate(product)).filteredOn(Violation::isError)
                .extracting(Violation::field).containsExactlyInAnyOrder("name", "version");
    }
}
