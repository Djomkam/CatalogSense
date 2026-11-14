package com.djomkam.catalogsense.ingestion;

import com.djomkam.catalogsense.TestProducts;
import com.djomkam.catalogsense.catalog.Product;
import com.djomkam.catalogsense.catalog.ProductRepository;
import com.djomkam.catalogsense.event.ProductsDeletedEvent;
import com.djomkam.catalogsense.event.ProductsUpsertedEvent;
import com.djomkam.catalogsense.health.HealthIssueRepository;
import com.djomkam.catalogsense.health.HealthService;
import com.djomkam.catalogsense.health.HealthSummary;
import com.djomkam.catalogsense.ingestion.validation.RuleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class IngestionServiceTest {

    private static final String CATALOG = "store-a";

    @Autowired IngestionService ingestionService;
    @Autowired HealthService healthService;
    @Autowired ProductRepository productRepository;
    @Autowired HealthIssueRepository healthIssueRepository;
    @Autowired ApplicationEvents applicationEvents;

    @BeforeEach
    void startFromEmptyTables() {
        healthIssueRepository.deleteAll();
        productRepository.deleteAll();
    }

    private Product storedProduct(String sku) {
        return productRepository.findByCatalogIdAndSku(CATALOG, sku).orElseThrow();
    }

    @Test
    void fullLoadAcceptsValidAndRejectsInvalidProducts() {
        IngestionReport report = ingestionService.ingest(CATALOG, List.of(
                TestProducts.valid("A", 1),
                TestProducts.withPrice("B", 1, new BigDecimal("-5")),
                TestProducts.withoutImage("C", 1)), IngestionMode.FULL);

        assertThat(report.mode()).isEqualTo(IngestionMode.FULL);
        assertThat(report.accepted()).isEqualTo(2);
        assertThat(report.rejected()).isEqualTo(1);
        assertThat(report.acceptedWithWarnings()).isEqualTo(1);
        assertThat(report.rejections()).singleElement().extracting(IngestionReport.Rejection::sku).isEqualTo("B");
        assertThat(productRepository.countByCatalogId(CATALOG)).isEqualTo(2);

        assertThat(applicationEvents.stream(CatalogIngestedEvent.class))
                .anyMatch(event -> event.catalogId().equals(CATALOG) && event.report().accepted() == 2);
        assertThat(applicationEvents.stream(ProductsUpsertedEvent.class))
                .anyMatch(event -> event.catalogId().equals(CATALOG) && event.products().size() == 2);
    }

    @Test
    void violationsAreStoredAsHealthIssuesNamedAfterTheirRule() {
        ingestionService.ingest(CATALOG, List.of(TestProducts.withoutImage("C", 1)), IngestionMode.PARTIAL);

        assertThat(healthIssueRepository.findAll()).singleElement().satisfies(issue -> {
            assertThat(issue.getSku()).isEqualTo("C");
            assertThat(issue.getRule()).isEqualTo(RuleId.IMAGE_URL.name());
            assertThat(issue.getField()).isEqualTo("imageUrl");
        });
    }

    @Test
    @Transactional // to read the lazy variants of the stored product
    void storedProductsAreNormalized() {
        var untidy = new ProductPayload("N", "  Trail Runner  ", "Northpeak", "Shoes", new BigDecimal("10.00"),
                " cad ", null, 1L, List.of(new VariantPayload("N-1", "Red", "M", null, null)));

        ingestionService.ingest(CATALOG, List.of(untidy), IngestionMode.PARTIAL);

        Product stored = storedProduct("N");
        assertThat(stored.getName()).isEqualTo("Trail Runner");
        assertThat(stored.getCurrency()).isEqualTo("CAD");
        assertThat(stored.getVariants()).singleElement().satisfies(variant -> assertThat(variant.isInStock()).isTrue());
    }

    @Test
    void fullLoadDeletesProductsMissingFromThePayload() {
        ingestionService.ingest(CATALOG, List.of(TestProducts.valid("A", 1), TestProducts.valid("B", 1)),
                IngestionMode.FULL);

        IngestionReport report = ingestionService.ingest(CATALOG, List.of(TestProducts.valid("A", 2)), IngestionMode.FULL);

        assertThat(report.deleted()).isEqualTo(1);
        assertThat(productRepository.findSkusByCatalogId(CATALOG)).containsExactly("A");
        assertThat(applicationEvents.stream(ProductsDeletedEvent.class))
                .anyMatch(event -> event.catalogId().equals(CATALOG) && event.skus().contains("B"));
    }

    @Test
    void partialUpdateKeepsOtherProducts() {
        ingestionService.ingest(CATALOG, List.of(TestProducts.valid("A", 1), TestProducts.valid("B", 1)),
                IngestionMode.FULL);

        IngestionReport report = ingestionService.ingest(CATALOG,
                List.of(TestProducts.withPrice("A", 2, new BigDecimal("99.00"))), IngestionMode.PARTIAL);

        assertThat(report.accepted()).isEqualTo(1);
        assertThat(report.deleted()).isZero();
        assertThat(productRepository.countByCatalogId(CATALOG)).isEqualTo(2);
        assertThat(storedProduct("A").getPrice()).isEqualByComparingTo("99.00");
    }

    @Test
    void staleVersionsAreSkipped() {
        ingestionService.ingest(CATALOG, List.of(TestProducts.withPrice("A", 5, new BigDecimal("10.00"))),
                IngestionMode.PARTIAL);

        IngestionReport report = ingestionService.ingest(CATALOG,
                List.of(TestProducts.withPrice("A", 4, new BigDecimal("1.00"))), IngestionMode.PARTIAL);

        assertThat(report.skippedStale()).isEqualTo(1);
        assertThat(storedProduct("A").getPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void rejectedUpdateKeepsThePreviousGoodVersion() {
        ingestionService.ingest(CATALOG, List.of(TestProducts.valid("A", 1)), IngestionMode.PARTIAL);

        ingestionService.ingest(CATALOG, List.of(TestProducts.withPrice("A", 2, null)), IngestionMode.PARTIAL);

        assertThat(storedProduct("A").getVersion()).isEqualTo(1);
        assertThat(healthService.summarize(CATALOG).skusWithErrors()).isEqualTo(1);
    }

    @Test
    void duplicateSkusInOneBatchAreRejected() {
        IngestionReport report = ingestionService.ingest(CATALOG,
                List.of(TestProducts.valid("A", 1), TestProducts.valid("A", 2)), IngestionMode.PARTIAL);

        assertThat(report.rejected()).isEqualTo(2);
        assertThat(productRepository.countByCatalogId(CATALOG)).isZero();
    }

    @Test
    void fixingAProductClearsItsIssuesAndRaisesTheScore() {
        ingestionService.ingest(CATALOG, List.of(TestProducts.valid("A", 1), TestProducts.withoutImage("B", 1)),
                IngestionMode.FULL);
        HealthSummary before = healthService.summarize(CATALOG);
        assertThat(before.score()).isEqualTo(50);
        assertThat(before.skusWithWarnings()).isEqualTo(1);

        ingestionService.ingest(CATALOG, List.of(TestProducts.valid("B", 2)), IngestionMode.PARTIAL);

        HealthSummary after = healthService.summarize(CATALOG);
        assertThat(after.score()).isEqualTo(100);
        assertThat(after.issuesByRule()).isEmpty();
    }
}
