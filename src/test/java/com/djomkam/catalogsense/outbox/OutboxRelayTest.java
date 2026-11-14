package com.djomkam.catalogsense.outbox;

import org.springframework.transaction.PlatformTransactionManager;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

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

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxProperties properties = new OutboxProperties("topic", 3,
            new OutboxProperties.Relay(true, 10, Duration.ofMillis(500), Duration.ofSeconds(1)), Duration.ofDays(7));
    private final OutboxRelay relay = new OutboxRelay(repository, kafka, properties, Clock.fixed(NOW, ZoneOffset.UTC),
            mock(PlatformTransactionManager.class));

    @Test
    void stopsAtTheFirstFailureToKeepPerProductOrder() {
        OutboxEvent first = row("e1");
        OutboxEvent second = row("e2");
        OutboxEvent third = row("e3");
        when(repository.lockNextBatch(any(Limit.class))).thenReturn(List.of(first, second, third));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        int published = relay.publishBatch();

        assertThat(published).isEqualTo(1);
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(second.getPublishedAt()).isNull();
        assertThat(second.getAttempts()).isEqualTo(1);
        assertThat(second.getLastError()).contains("broker down");
        assertThat(third.getAttempts()).isZero();
        verify(kafka, times(2)).send(any(ProducerRecord.class));
    }

    @Test
    void sendsKeyAndHeaders() {
        OutboxEvent row = row("e1");
        when(repository.lockNextBatch(any(Limit.class))).thenReturn(List.of(row));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.publishBatch();

        verify(kafka).send(argThat((ProducerRecord<String, String> r) ->
                r.topic().equals("topic")
                        && r.key().equals("store:SKU")
                        && new String(r.headers().lastHeader("eventId").value()).equals("e1")
                        && new String(r.headers().lastHeader("eventType").value()).equals(ProductEventMessage.UPSERTED)));
    }

    private static OutboxEvent row(String eventId) {
        return new OutboxEvent(eventId, ProductEventMessage.UPSERTED, "store", "store:SKU", "{}", NOW);
    }
}
