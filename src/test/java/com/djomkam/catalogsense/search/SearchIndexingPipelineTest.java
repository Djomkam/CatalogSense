package com.djomkam.catalogsense.search;

import com.djomkam.catalogsense.TestProducts;
import com.djomkam.catalogsense.domain.HealthIssueRepository;
import com.djomkam.catalogsense.domain.ProductRepository;
import com.djomkam.catalogsense.outbox.OutboxEventRepository;
import com.djomkam.catalogsense.service.IngestionMode;
import com.djomkam.catalogsense.service.IngestionService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Ingestion -> outbox -> relay -> Kafka -> search consumer, on an embedded broker.
 * OpenSearch itself is replaced by a mock indexer (see OpenSearchClientTest and
 * OpenSearchIntegrationTest for the OpenSearch side).
 */
@SpringBootTest(properties = {
        // Own database: other test contexts leave unpublished outbox rows in the shared one.
        "spring.datasource.url=jdbc:h2:mem:pipelinetest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.auto-create=true",
        "catalogsense.outbox.relay.enabled=true",
        "catalogsense.outbox.relay.poll-interval=100ms",
        "catalogsense.outbox.topic=pipeline.product-events",
        "catalogsense.search.enabled=true",
        "catalogsense.search.consumer.retries=1",
        "catalogsense.search.consumer.retry-backoff=50ms"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = {"pipeline.product-events", "pipeline.product-events.DLT"})
@DirtiesContext
class SearchIndexingPipelineTest {

    private static final String CATALOG = "pipeline-store";

    @MockitoBean ProductIndexer indexer;

    @Autowired IngestionService ingestion;
    @Autowired OutboxEventRepository outbox;
    @Autowired ProductRepository products;
    @Autowired HealthIssueRepository issues;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired EmbeddedKafkaBroker broker;

    @BeforeEach
    void clean() {
        outbox.deleteAll();
        issues.deleteAll();
        products.deleteAll();
    }

    @Test
    void ingestedProductsAreIndexedAndRemovedProductsDeleted() {
        when(indexer.upsert(any())).thenReturn(true);

        ingestion.ingest(CATALOG, List.of(TestProducts.valid("A", 1), TestProducts.valid("B", 1)), IngestionMode.FULL);
        ingestion.ingest(CATALOG, List.of(TestProducts.valid("A", 2)), IngestionMode.FULL);

        ArgumentCaptor<ProductDocument> docs = ArgumentCaptor.forClass(ProductDocument.class);
        verify(indexer, timeout(20_000).times(3)).upsert(docs.capture());
        verify(indexer, timeout(20_000)).delete(CATALOG, "B");

        List<ProductDocument> forA = docs.getAllValues().stream().filter(d -> d.sku().equals("A")).toList();
        assertThat(forA).extracting(ProductDocument::version).containsExactly(1L, 2L);
        assertThat(forA.get(1).colors()).containsExactly("Black");
        assertThat(forA.get(1).brand()).isEqualTo("Northpeak");
    }

    @Test
    void unreadableMessagesGoToTheDeadLetterTopic() {
        kafka.send("pipeline.product-events", CATALOG + ":BAD", "{not json");

        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-reader", "true", broker);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> dlt = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(dlt, "pipeline.product-events.DLT");
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(dlt, "pipeline.product-events.DLT", Duration.ofSeconds(20));

            assertThat(record.key()).isEqualTo(CATALOG + ":BAD");
            assertThat(record.value()).isEqualTo("{not json");
            assertThat(new String(record.headers().lastHeader("kafka_dlt-exception-cause-fqcn").value()))
                    .contains("JsonParseException");
        }
        verify(indexer, never()).upsert(any());
        verify(indexer, never()).delete(any(), any());
    }
}
