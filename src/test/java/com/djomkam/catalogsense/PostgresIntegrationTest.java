package com.djomkam.catalogsense;

import com.djomkam.catalogsense.health.HealthService;
import com.djomkam.catalogsense.health.IssueView;
import com.djomkam.catalogsense.health.Severity;
import com.djomkam.catalogsense.ingestion.IngestionMode;
import com.djomkam.catalogsense.ingestion.IngestionReport;
import com.djomkam.catalogsense.ingestion.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Flyway migrations, an ingestion, the bulk delete and the optional-filter issue query against a
 * real PostgreSQL (null query parameters behave differently there than on H2). Skipped when Docker is not available.
 */
@SpringBootTest(properties = "catalogsense.search.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {

    private static final String CATALOG = "pg";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired IngestionService ingestionService;
    @Autowired HealthService healthService;

    @Test
    void ingestsScoresFiltersAndDeletesAgainstRealPostgres() {
        IngestionReport report = ingestionService.ingest(CATALOG,
                List.of(TestProducts.valid("A", 1), TestProducts.withoutImage("B", 1)), IngestionMode.FULL);

        assertThat(report.accepted()).isEqualTo(2);
        assertThat(healthService.summarize(CATALOG).score()).isEqualTo(50);
        assertThat(healthService.findIssues(CATALOG, null, null, PageRequest.of(0, 10)))
                .extracting(IssueView::sku).containsExactly("B");
        assertThat(healthService.findIssues(CATALOG, "IMAGE_URL", Severity.WARNING, PageRequest.of(0, 10)))
                .hasSize(1);

        IngestionReport fullLoadWithoutB = ingestionService.ingest(CATALOG,
                List.of(TestProducts.valid("A", 2)), IngestionMode.FULL);

        assertThat(fullLoadWithoutB.deleted()).isEqualTo(1);
        assertThat(healthService.summarize(CATALOG).productCount()).isEqualTo(1);
    }
}
