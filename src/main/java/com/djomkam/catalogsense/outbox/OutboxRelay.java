package com.djomkam.catalogsense.outbox;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox and publishes pending rows to Kafka (polling publisher pattern).
 *
 * <ul>
 *   <li><b>At-least-once:</b> a row is marked published only after the broker acknowledged it.
 *       If the app dies in between, the row is sent again, so consumers de-duplicate on eventId.</li>
 *   <li><b>Ordering:</b> rows go out in id order and the batch stops at the first failure, so a
 *       later change of a product is never sent before an earlier one.</li>
 *   <li><b>Scaling out:</b> rows are locked with SKIP LOCKED; several instances share the work.</li>
 * </ul>
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "catalogsense.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafka,
                       OutboxProperties properties, Clock clock, PlatformTransactionManager txManager) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafka = kafka;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${catalogsense.outbox.relay.poll-interval:500ms}")
    public void poll() {
        int sent;
        do {
            sent = publishBatch();
        } while (sent == properties.relay().batchSize());
    }

    /**
     * Locks, sends and marks one batch in its own transaction. A TransactionTemplate is used
     * instead of @Transactional because poll() calls this method on the same instance, which
     * would bypass the Spring proxy.
     *
     * @return number of rows published in this batch
     */
    public int publishBatch() {
        Integer published = tx.execute(status -> publishLockedBatch());
        return published == null ? 0 : published;
    }

    private int publishLockedBatch() {
        List<OutboxEvent> batch = outboxEventRepository.lockNextBatch(Limit.of(properties.relay().batchSize()));
        int published = 0;
        for (OutboxEvent row : batch) {
            try {
                ProducerRecord<String, String> record =
                        new ProducerRecord<>(properties.topic(), row.getMessageKey(), row.getPayload());
                record.headers().add("eventType", row.getEventType().getBytes());
                record.headers().add("eventId", row.getEventId().getBytes());
                kafka.send(record).get(properties.relay().sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                row.markPublished(clock.instant());
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                row.markFailed("interrupted");
                break;
            } catch (Exception e) {
                row.markFailed(e.toString());
                log.warn("Outbox relay failed on event {} (attempt {}); will retry next cycle: {}",
                        row.getEventId(), row.getAttempts(), e.toString());
                break;
            }
        }
        if (published > 0) {
            log.debug("Outbox relay published {} event(s)", published);
        }
        return published;
    }
}
