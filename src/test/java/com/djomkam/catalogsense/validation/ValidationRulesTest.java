package com.djomkam.catalogsense.validation;

import com.djomkam.catalogsense.TestProducts;
import com.djomkam.catalogsense.api.ProductPayload;
import com.djomkam.catalogsense.api.VariantPayload;
import com.djomkam.catalogsense.domain.Severity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationRulesTest {

    private final PriceRule price = new PriceRule();
    private final ImageUrlRule image = new ImageUrlRule();
    private final VariantRule variants = new VariantRule();
    private final RequiredFieldsRule required = new RequiredFieldsRule();

    @Test
    void validProductHasNoViolations() {
        ProductPayload p = TestProducts.valid("SKU-1", 1);
        assertThat(price.validate(p)).isEmpty();
        assertThat(image.validate(p)).isEmpty();
        assertThat(variants.validate(p)).isEmpty();
        assertThat(required.validate(p)).isEmpty();
    }

    @Test
    void zeroPriceIsAnError() {
        List<Violation> v = price.validate(TestProducts.withPrice("SKU-1", 1, BigDecimal.ZERO));
        assertThat(v).singleElement().satisfies(x -> {
            assertThat(x.severity()).isEqualTo(Severity.ERROR);
            assertThat(x.field()).isEqualTo("price");
        });
    }

    @Test
    void unknownCurrencyIsAnError() {
        ProductPayload p = TestProducts.valid("SKU-1", 1);
        ProductPayload bad = new ProductPayload(p.sku(), p.name(), p.brand(), p.category(), p.price(), "DOLLARS",
                p.imageUrl(), p.version(), p.variants());
        assertThat(price.validate(bad)).extracting(Violation::field).containsExactly("currency");
    }

    @Test
    void missingOrInsecureImageIsAWarning() {
        assertThat(image.validate(TestProducts.withoutImage("SKU-1", 1)))
                .singleElement().extracting(Violation::severity).isEqualTo(Severity.WARNING);

        ProductPayload p = TestProducts.valid("SKU-1", 1);
        ProductPayload http = new ProductPayload(p.sku(), p.name(), p.brand(), p.category(), p.price(), p.currency(),
                "http://cdn.example.com/a.jpg", p.version(), p.variants());
        assertThat(image.validate(http)).singleElement().extracting(Violation::severity).isEqualTo(Severity.WARNING);

        ProductPayload ftp = new ProductPayload(p.sku(), p.name(), p.brand(), p.category(), p.price(), p.currency(),
                "ftp://files/a.jpg", p.version(), p.variants());
        assertThat(image.validate(ftp)).hasSize(1);
    }

    @Test
    void duplicateVariantSkuIsAnError() {
        ProductPayload p = TestProducts.valid("SKU-1", 1);
        ProductPayload dup = new ProductPayload(p.sku(), p.name(), p.brand(), p.category(), p.price(), p.currency(),
                p.imageUrl(), p.version(), List.of(
                new VariantPayload("V1", "Red", "M", null, true),
                new VariantPayload("V1", "Red", "L", null, true)));
        assertThat(variants.validate(dup)).extracting(Violation::severity).containsExactly(Severity.ERROR);
    }

    @Test
    void allVariantsOutOfStockIsAWarning() {
        ProductPayload p = TestProducts.valid("SKU-1", 1);
        ProductPayload oos = new ProductPayload(p.sku(), p.name(), p.brand(), p.category(), p.price(), p.currency(),
                p.imageUrl(), p.version(), List.of(new VariantPayload("V1", "Red", "M", null, false)));
        assertThat(variants.validate(oos)).extracting(Violation::severity).containsExactly(Severity.WARNING);
    }

    @Test
    void missingNameAndVersionAreErrors() {
        ProductPayload p = new ProductPayload("SKU-1", " ", "B", "C", BigDecimal.ONE, "CAD", null, null, null);
        assertThat(required.validate(p)).filteredOn(Violation::isError)
                .extracting(Violation::field).containsExactlyInAnyOrder("name", "version");
    }
}
