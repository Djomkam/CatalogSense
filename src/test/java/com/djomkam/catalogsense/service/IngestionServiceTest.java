package com.djomkam.catalogsense.service;

import com.djomkam.catalogsense.TestProducts;
import com.djomkam.catalogsense.api.HealthSummary;
import com.djomkam.catalogsense.api.IngestionReport;
import com.djomkam.catalogsense.domain.HealthIssueRepository;
import com.djomkam.catalogsense.domain.ProductRepository;
import com.djomkam.catalogsense.event.CatalogIngestedEvent;
import com.djomkam.catalogsense.event.ProductsDeletedEvent;
import com.djomkam.catalogsense.event.ProductsUpsertedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class IngestionServiceTest {

    private static final String CATALOG = "store-a";

    @Autowired IngestionService ingestion;
    @Autowired HealthService health;
    @Autowired ProductRepository products;
    @Autowired HealthIssueRepository issues;
    @Autowired ApplicationEvents applicationEvents;

    @BeforeEach
    void clean() {
        issues.deleteAll();
        products.deleteAll();
    }

    @Test
    void fullLoadAcceptsValidAndRejectsInvalidProducts() {
        IngestionReport r = ingestion.ingest(CATALOG, List.of(
                TestProducts.valid("A", 1),
                TestProducts.withPrice("B", 1, new BigDecimal("-5")),
                TestProducts.withoutImage("C", 1)), IngestionMode.FULL);

        assertThat(r.accepted()).isEqualTo(2);
        assertThat(r.rejected()).isEqualTo(1);
        assertThat(r.warnings()).isEqualTo(1);
        assertThat(r.rejections()).singleElement().extracting(IngestionReport.Rejection::sku).isEqualTo("B");
        assertThat(products.countByCatalogId(CATALOG)).isEqualTo(2);

        assertThat(applicationEvents.stream(CatalogIngestedEvent.class))
                .anyMatch(e -> e.catalogId().equals(CATALOG) && e.report().accepted() == 2);
        assertThat(applicationEvents.stream(ProductsUpsertedEvent.class))
                .anyMatch(e -> e.catalogId().equals(CATALOG) && e.products().size() == 2);
    }

    @Test
    void fullLoadDeletesProductsMissingFromThePayload() {
        ingestion.ingest(CATALOG, List.of(TestProducts.valid("A", 1), TestProducts.valid("B", 1)), IngestionMode.FULL);

        IngestionReport r = ingestion.ingest(CATALOG, List.of(TestProducts.valid("A", 2)), IngestionMode.FULL);

        assertThat(r.deleted()).isEqualTo(1);
        assertThat(products.findSkusByCatalogId(CATALOG)).containsExactly("A");

        assertThat(applicationEvents.stream(ProductsDeletedEvent.class))
                .anyMatch(e -> e.catalogId().equals(CATALOG) && e.skus().contains("B"));
    }

    @Test
    void partialUpdateKeepsOtherProducts() {
        ingestion.ingest(CATALOG, List.of(TestProducts.valid("A", 1), TestProducts.valid("B", 1)), IngestionMode.FULL);

        IngestionReport r = ingestion.ingest(CATALOG,
                List.of(TestProducts.withPrice("A", 2, new BigDecimal("99.00"))), IngestionMode.PARTIAL);

        assertThat(r.accepted()).isEqualTo(1);
        assertThat(r.deleted()).isZero();
        assertThat(products.countByCatalogId(CATALOG)).isEqualTo(2);
        assertThat(products.findByCatalogIdAndSku(CATALOG, "A").orElseThrow().getPrice()).isEqualByComparingTo("99.00");
    }

    @Test
    void staleVersionsAreSkipped() {
        ingestion.ingest(CATALOG, List.of(TestProducts.withPrice("A", 5, new BigDecimal("10.00"))), IngestionMode.PARTIAL);

        IngestionReport r = ingestion.ingest(CATALOG,
                List.of(TestProducts.withPrice("A", 4, new BigDecimal("1.00"))), IngestionMode.PARTIAL);

        assertThat(r.skippedStale()).isEqualTo(1);
        assertThat(products.findByCatalogIdAndSku(CATALOG, "A").orElseThrow().getPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void rejectedUpdateKeepsThePreviousGoodVersion() {
        ingestion.ingest(CATALOG, List.of(TestProducts.valid("A", 1)), IngestionMode.PARTIAL);

        ingestion.ingest(CATALOG, List.of(TestProducts.withPrice("A", 2, null)), IngestionMode.PARTIAL);

        assertThat(products.findByCatalogIdAndSku(CATALOG, "A").orElseThrow().getVersion()).isEqualTo(1);
        assertThat(health.summary(CATALOG).skusWithErrors()).isEqualTo(1);
    }

    @Test
    void duplicateSkusInOneBatchAreRejected() {
        IngestionReport r = ingestion.ingest(CATALOG,
                List.of(TestProducts.valid("A", 1), TestProducts.valid("A", 2)), IngestionMode.PARTIAL);

        assertThat(r.rejected()).isEqualTo(2);
        assertThat(products.countByCatalogId(CATALOG)).isZero();
    }

    @Test
    void fixingAProductClearsItsIssuesAndRaisesTheScore() {
        ingestion.ingest(CATALOG, List.of(TestProducts.valid("A", 1), TestProducts.withoutImage("B", 1)), IngestionMode.FULL);
        HealthSummary before = health.summary(CATALOG);
        assertThat(before.score()).isEqualTo(50);
        assertThat(before.skusWithWarnings()).isEqualTo(1);

        ingestion.ingest(CATALOG, List.of(TestProducts.valid("B", 2)), IngestionMode.PARTIAL);

        HealthSummary after = health.summary(CATALOG);
        assertThat(after.score()).isEqualTo(100);
        assertThat(after.issuesByRule()).isEmpty();
    }
}
