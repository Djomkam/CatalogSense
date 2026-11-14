package com.djomkam.catalogsense.ingestion;

import com.djomkam.catalogsense.TestProducts;
import com.djomkam.catalogsense.catalog.ProductData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductPayloadTest {

    @Test
    void normalizingTrimsTheNameUppercasesTheCurrencyAndDefaultsStock() {
        var untidy = new ProductPayload("SKU", "  Parka ", "Brand", "Coats", BigDecimal.TEN, " cad", null, 1L,
                List.of(new VariantPayload("SKU-M", "Navy", "M", null, null)));

        ProductPayload normalized = untidy.normalized();

        assertThat(normalized.name()).isEqualTo("Parka");
        assertThat(normalized.currency()).isEqualTo("CAD");
        assertThat(normalized.variants()).singleElement().satisfies(variant -> assertThat(variant.inStock()).isTrue());
    }

    @Test
    void normalizingKeepsMissingValuesMissing() {
        var sparse = new ProductPayload("SKU", null, null, null, null, null, null, null, null);

        assertThat(sparse.normalized()).isEqualTo(sparse);
    }

    @Test
    void convertsToProductData() {
        ProductData data = TestProducts.valid("A", 3).toProductData();

        assertThat(data.version()).isEqualTo(3);
        assertThat(data.variants()).singleElement().satisfies(variant -> {
            assertThat(variant.sku()).isEqualTo("A-9");
            assertThat(variant.inStock()).isTrue();
        });
    }

    @Test
    void aPayloadWithoutVersionCannotBecomeAProduct() {
        var withoutVersion = new ProductPayload("SKU", "Name", null, null, BigDecimal.ONE, "CAD", null, null, null);

        assertThatThrownBy(withoutVersion::toProductData).isInstanceOf(IllegalArgumentException.class);
    }
}
