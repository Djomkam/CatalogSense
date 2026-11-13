package com.djomkam.catalogsense.search;

import com.djomkam.catalogsense.event.ProductSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDocumentTest {

    @Test
    void variantColorsAndSizesAreFlattenedForFacets() {
        var snapshot = new ProductSnapshot("demo", "TEE", "Tee", "Acme", "Tops", new BigDecimal("20"), "CAD", null,
                3, true, Instant.EPOCH, List.of(
                new ProductSnapshot.VariantSnapshot("TEE-RS", "Red", "S", null, true),
                new ProductSnapshot.VariantSnapshot("TEE-RM", "Red", "M", null, false),
                new ProductSnapshot.VariantSnapshot("TEE-BX", "Blue", " ", null, true),
                new ProductSnapshot.VariantSnapshot("TEE-NO", null, "M", null, true)));

        ProductDocument doc = ProductDocument.from(snapshot);

        assertThat(doc.id()).isEqualTo("demo:TEE");
        assertThat(doc.colors()).containsExactly("Blue", "Red");
        assertThat(doc.sizes()).containsExactly("M", "S");
        assertThat(doc.version()).isEqualTo(3);
    }
}
