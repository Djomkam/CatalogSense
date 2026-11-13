package com.djomkam.catalogsense;

import com.djomkam.catalogsense.api.IngestionReport;
import com.djomkam.catalogsense.service.HealthService;
import com.djomkam.catalogsense.service.IngestionMode;
import com.djomkam.catalogsense.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the Flyway migration and an ingestion against a real PostgreSQL. Skipped when Docker is not available. */
@SpringBootTest(properties = "catalogsense.search.enabled=false")
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired IngestionService ingestion;
    @Autowired HealthService health;

    @Test
    void ingestsAgainstRealPostgres() {
        IngestionReport r = ingestion.ingest("pg", List.of(TestProducts.valid("A", 1), TestProducts.withoutImage("B", 1)),
                IngestionMode.FULL);

        assertThat(r.accepted()).isEqualTo(2);
        assertThat(health.summary("pg").score()).isEqualTo(50);
    }
}
