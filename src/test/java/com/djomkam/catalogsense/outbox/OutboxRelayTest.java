package com.djomkam.catalogsense.outbox;

import com.djomkam.catalogsense.event.ProductEventMessage;
import com.djomkam.catalogsense.event.ProductEventType;
import org.springframework.transaction.PlatformTransactionManager;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Relay behaviour on broker failures, without Spring or Kafka. */
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final OutboxProperties properties = new OutboxProperties("topic", 3,
            new OutboxProperties.Relay(true, 10, Duration.ofMillis(500), Duration.ofSeconds(1)), Duration.ofDays(7));
    private final OutboxRelay relay = new OutboxRelay(outboxEventRepository, kafkaTemplate, properties, Clock.fixed(NOW, ZoneOffset.UTC),
            mock(PlatformTransactionManager.class));

    @Test
    void stopsAtTheFirstFailureToKeepPerProductOrder() {
        OutboxEvent first = outboxEvent("e1");
        OutboxEvent second = outboxEvent("e2");
        OutboxEvent third = outboxEvent("e3");
        when(outboxEventRepository.lockNextBatch(any(Limit.class))).thenReturn(List.of(first, second, third));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        int published = relay.publishNextBatch();

        assertThat(published).isEqualTo(1);
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(second.getPublishedAt()).isNull();
        assertThat(second.getAttempts()).isEqualTo(1);
        assertThat(second.getLastError()).contains("broker down");
        assertThat(third.getAttempts()).isZero();
        verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
    }

    @Test
    void sendsKeyAndHeaders() {
        OutboxEvent event = outboxEvent("e1");
        when(outboxEventRepository.lockNextBatch(any(Limit.class))).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.publishNextBatch();

        verify(kafkaTemplate).send(argThat((ProducerRecord<String, String> producerRecord) ->
                producerRecord.topic().equals("topic")
                        && producerRecord.key().equals("store:SKU")
                        && header(producerRecord, ProductEventMessage.EVENT_ID_HEADER).equals("e1")
                        && header(producerRecord, ProductEventMessage.EVENT_TYPE_HEADER).equals("ProductUpserted")));
    }

    private static String header(ProducerRecord<String, String> producerRecord, String name) {
        return new String(producerRecord.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static OutboxEvent outboxEvent(String eventId) {
        return new OutboxEvent(eventId, ProductEventType.UPSERTED, "store", "store:SKU", "{}", NOW);
    }
}
