package com.djomkam.catalogsense.outbox;

import com.djomkam.catalogsense.event.ProductEventMessage;
import com.djomkam.catalogsense.event.ProductEventType;
import com.djomkam.catalogsense.TestProducts;
import com.djomkam.catalogsense.health.HealthIssueRepository;
import com.djomkam.catalogsense.catalog.ProductRepository;
import com.djomkam.catalogsense.event.ProductsDeletedEvent;
import com.djomkam.catalogsense.ingestion.IngestionMode;
import com.djomkam.catalogsense.ingestion.IngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves the outbox rows commit and roll back together with the catalog change. */
@SpringBootTest
@ActiveProfiles("test")
class OutboxWriterTest {

    private static final String CATALOG = "outbox-store";

    @Autowired IngestionService ingestionService;
    @Autowired OutboxWriter outboxWriter;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ProductRepository productRepository;
    @Autowired HealthIssueRepository healthIssueRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired MeterRegistry meterRegistry;

    @BeforeEach
    void startFromEmptyTables() {
        outboxEventRepository.deleteAll();
        healthIssueRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void committedIngestionWritesOneOutboxRowPerChangedProduct() throws Exception {
        ingestionService.ingest(CATALOG, List.of(TestProducts.valid("A", 1), TestProducts.valid("B", 1)), IngestionMode.FULL);
        ingestionService.ingest(CATALOG, List.of(TestProducts.valid("A", 2)), IngestionMode.FULL); // updates A, deletes B

        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll().stream()
                .sorted(Comparator.comparing(OutboxEvent::getId)).toList();

        assertThat(outboxEvents).extracting(OutboxEvent::getEventType, OutboxEvent::getMessageKey).containsExactly(
                tuple(ProductEventType.UPSERTED, CATALOG + ":A"),
                tuple(ProductEventType.UPSERTED, CATALOG + ":B"),
                tuple(ProductEventType.UPSERTED, CATALOG + ":A"),
                tuple(ProductEventType.DELETED, CATALOG + ":B"));
        assertThat(outboxEvents).allSatisfy(outboxEvent -> assertThat(outboxEvent.getPublishedAt()).isNull());

        // The payload is a full snapshot (variants included), readable without any JPA session.
        ProductEventMessage update = objectMapper.readValue(outboxEvents.get(2).getPayload(), ProductEventMessage.class);
        assertThat(update.version()).isEqualTo(2L);
        assertThat(update.product().variants()).singleElement()
                .satisfies(variant -> assertThat(variant.sku()).isEqualTo("A-9"));

        ProductEventMessage delete = objectMapper.readValue(outboxEvents.get(3).getPayload(), ProductEventMessage.class);
        assertThat(delete.product()).isNull();
        assertThat(delete.sku()).isEqualTo("B");
    }

    @Test
    void rolledBackIngestionLeavesNoOutboxRowAndNoMetric() {
        double before = ingestionCount();

        transactionTemplate.executeWithoutResult(status -> {
            ingestionService.ingest(CATALOG, List.of(TestProducts.valid("A", 1)), IngestionMode.PARTIAL);
            status.setRollbackOnly();
        });

        assertThat(outboxEventRepository.count()).isZero();
        assertThat(productRepository.countByCatalogId(CATALOG)).isZero();
        assertThat(ingestionCount()).isEqualTo(before); // AFTER_COMMIT listener did not run
    }

    @Test
    void committedIngestionIsCountedOnceAfterCommit() {
        double before = ingestionCount();

        ingestionService.ingest(CATALOG, List.of(TestProducts.valid("A", 1)), IngestionMode.PARTIAL);

        assertThat(ingestionCount()).isEqualTo(before + 1);
    }

    @Test
    void writerRefusesToRunOutsideATransaction() {
        assertThatThrownBy(() -> outboxWriter.onProductsDeleted(new ProductsDeletedEvent(CATALOG, Set.of("X"), Instant.now())))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private double ingestionCount() {
        var counter = meterRegistry.find("catalogsense.ingestions")
                .tags("catalog", CATALOG, "mode", "PARTIAL").counter();
        return counter == null ? 0 : counter.count();
    }
}
