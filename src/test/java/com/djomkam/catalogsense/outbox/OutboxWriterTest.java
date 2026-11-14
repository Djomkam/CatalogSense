package com.djomkam.catalogsense.outbox;

import com.djomkam.catalogsense.TestProducts;
import com.djomkam.catalogsense.domain.HealthIssueRepository;
import com.djomkam.catalogsense.domain.ProductRepository;
import com.djomkam.catalogsense.event.ProductsDeletedEvent;
import com.djomkam.catalogsense.service.IngestionMode;
import com.djomkam.catalogsense.service.IngestionService;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves the outbox rows commit and roll back together with the catalog change. */
@SpringBootTest
@ActiveProfiles("test")
class OutboxWriterTest {

    private static final String CATALOG = "outbox-store";

    @Autowired IngestionService ingestion;
    @Autowired OutboxWriter writer;
    @Autowired OutboxEventRepository outbox;
    @Autowired ProductRepository products;
    @Autowired HealthIssueRepository issues;
    @Autowired TransactionTemplate tx;
    @Autowired ObjectMapper objectMapper;
    @Autowired MeterRegistry meterRegistry;

    @BeforeEach
    void clean() {
        outbox.deleteAll();
        issues.deleteAll();
        products.deleteAll();
    }

    @Test
    void committedIngestionWritesOneOutboxRowPerChangedProduct() throws Exception {
        ingestion.ingest(CATALOG, List.of(TestProducts.valid("A", 1), TestProducts.valid("B", 1)), IngestionMode.FULL);
        ingestion.ingest(CATALOG, List.of(TestProducts.valid("A", 2)), IngestionMode.FULL); // updates A, deletes B

        List<OutboxEvent> rows = outbox.findAll().stream()
                .sorted((x, y) -> Long.compare(x.getId(), y.getId())).toList();

        assertThat(rows).extracting(OutboxEvent::getEventType, OutboxEvent::getMessageKey).containsExactly(
                org.assertj.core.groups.Tuple.tuple(ProductEventMessage.UPSERTED, CATALOG + ":A"),
                org.assertj.core.groups.Tuple.tuple(ProductEventMessage.UPSERTED, CATALOG + ":B"),
                org.assertj.core.groups.Tuple.tuple(ProductEventMessage.UPSERTED, CATALOG + ":A"),
                org.assertj.core.groups.Tuple.tuple(ProductEventMessage.DELETED, CATALOG + ":B"));
        assertThat(rows).allSatisfy(r -> assertThat(r.getPublishedAt()).isNull());

        // The payload is a full snapshot (variants included), readable without any JPA session.
        ProductEventMessage update = objectMapper.readValue(rows.get(2).getPayload(), ProductEventMessage.class);
        assertThat(update.version()).isEqualTo(2L);
        assertThat(update.product().variants()).singleElement()
                .satisfies(v -> assertThat(v.sku()).isEqualTo("A-9"));

        ProductEventMessage delete = objectMapper.readValue(rows.get(3).getPayload(), ProductEventMessage.class);
        assertThat(delete.product()).isNull();
        assertThat(delete.sku()).isEqualTo("B");
    }

    @Test
    void rolledBackIngestionLeavesNoOutboxRowAndNoMetric() {
        double before = ingestionCount();

        tx.executeWithoutResult(status -> {
            ingestion.ingest(CATALOG, List.of(TestProducts.valid("A", 1)), IngestionMode.PARTIAL);
            status.setRollbackOnly();
        });

        assertThat(outbox.count()).isZero();
        assertThat(products.countByCatalogId(CATALOG)).isZero();
        assertThat(ingestionCount()).isEqualTo(before); // AFTER_COMMIT listener did not run
    }

    @Test
    void committedIngestionIsCountedOnceAfterCommit() {
        double before = ingestionCount();

        ingestion.ingest(CATALOG, List.of(TestProducts.valid("A", 1)), IngestionMode.PARTIAL);

        assertThat(ingestionCount()).isEqualTo(before + 1);
    }

    @Test
    void writerRefusesToRunOutsideATransaction() {
        assertThatThrownBy(() -> writer.on(new ProductsDeletedEvent(CATALOG, Set.of("X"), Instant.now())))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private double ingestionCount() {
        var counter = meterRegistry.find("catalogsense.ingestions")
                .tags("catalog", CATALOG, "mode", "PARTIAL").counter();
        return counter == null ? 0 : counter.count();
    }
}
