package com.djomkam.catalogsense.outbox;

import com.djomkam.catalogsense.event.ProductEventMessage;
import com.djomkam.catalogsense.event.ProductEventType;
import com.djomkam.catalogsense.TestProducts;
import com.djomkam.catalogsense.health.HealthIssueRepository;
import com.djomkam.catalogsense.catalog.ProductRepository;
import com.djomkam.catalogsense.ingestion.IngestionMode;
import com.djomkam.catalogsense.ingestion.IngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** End to end: ingestion -> outbox -> relay -> Kafka, against an in-memory broker (no Docker needed). */
@SpringBootTest(properties = {
        // Own database: other test contexts leave unpublished outbox rows in the shared one.
        "spring.datasource.url=jdbc:h2:mem:relaytest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.auto-create=true",
        "catalogsense.outbox.relay.enabled=true",
        "catalogsense.outbox.relay.poll-interval=100ms",
        "catalogsense.outbox.topic=test.product-events"
})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, topics = "test.product-events")
@DirtiesContext
class OutboxRelayIntegrationTest {

    private static final String CATALOG = "relay-store";

    @Autowired IngestionService ingestionService;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired ProductRepository productRepository;
    @Autowired HealthIssueRepository healthIssueRepository;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired ObjectMapper objectMapper;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        healthIssueRepository.deleteAll();
        productRepository.deleteAll();
        Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps("test-consumer", "true", broker);
        consumerProperties.put("auto.offset.reset", "earliest");
        consumer = new DefaultKafkaConsumerFactory<>(consumerProperties, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, "test.product-events");
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void committedChangesReachKafkaKeyedByProduct() throws Exception {
        ingestionService.ingest(CATALOG, List.of(TestProducts.valid("A", 1), TestProducts.valid("B", 1)), IngestionMode.FULL);
        ingestionService.ingest(CATALOG, List.of(TestProducts.valid("A", 2)), IngestionMode.FULL);

        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500)).forEach(received::add);
            assertThat(received).hasSize(4);
        });

        // Every change of product A is on the same partition, in order: version 1 then 2.
        List<ConsumerRecord<String, String>> productA = received.stream()
                .filter(message -> message.key().equals(CATALOG + ":A")).toList();
        assertThat(productA).extracting(ConsumerRecord::partition).containsOnly(productA.getFirst().partition());
        assertThat(productA).extracting(message -> read(message).version()).containsExactly(1L, 2L);

        assertThat(received).filteredOn(message -> message.key().equals(CATALOG + ":B"))
                .extracting(message -> read(message).type())
                .containsExactly(ProductEventType.UPSERTED, ProductEventType.DELETED);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(outboxEventRepository.countByPublishedAtIsNull()).isZero());
    }

    private ProductEventMessage read(ConsumerRecord<String, String> message) {
        try {
            return objectMapper.readValue(message.value(), ProductEventMessage.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
