package com.djomkam.catalogsense.outbox;

import com.djomkam.catalogsense.event.ProductEventMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
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
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public OutboxRelay(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, String> kafkaTemplate,
                       OutboxProperties properties, Clock clock, PlatformTransactionManager transactionManager) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Publishes batches until the outbox is drained (a short batch means nothing is left). */
    @Scheduled(fixedDelayString = "${catalogsense.outbox.relay.poll-interval:500ms}")
    public void publishPendingEvents() {
        int publishedInBatch;
        do {
            publishedInBatch = publishNextBatch();
        } while (publishedInBatch == properties.relay().batchSize());
    }

    /**
     * Locks, sends and marks one batch in its own transaction. A TransactionTemplate is used
     * instead of @Transactional because {@link #publishPendingEvents()} calls this method on the
     * same instance, which would bypass the Spring proxy.
     *
     * @return number of rows published in this batch
     */
    public int publishNextBatch() {
        Integer published = transactionTemplate.execute(status -> publishLockedBatch());
        return published == null ? 0 : published;
    }

    private int publishLockedBatch() {
        List<OutboxEvent> batch = outboxEventRepository.lockNextBatch(Limit.of(properties.relay().batchSize()));
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(toProducerRecord(event))
                        .get(properties.relay().sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                event.markPublished(clock.instant());
                published++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.markFailed("interrupted");
                break;
            } catch (Exception e) {
                event.markFailed(e.toString());
                log.warn("Outbox relay failed on event {} (attempt {}); will retry next cycle: {}",
                        event.getEventId(), event.getAttempts(), e.toString());
                break;
            }
        }
        if (published > 0) {
            log.debug("Outbox relay published {} event(s)", published);
        }
        return published;
    }

    private ProducerRecord<String, String> toProducerRecord(OutboxEvent event) {
        var producerRecord = new ProducerRecord<>(properties.topic(), event.getMessageKey(), event.getPayload());
        producerRecord.headers()
                .add(ProductEventMessage.EVENT_TYPE_HEADER,
                        event.getEventType().wireValue().getBytes(StandardCharsets.UTF_8))
                .add(ProductEventMessage.EVENT_ID_HEADER, event.getEventId().getBytes(StandardCharsets.UTF_8));
        return producerRecord;
    }
}
