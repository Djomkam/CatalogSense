package com.djomkam.catalogsense.search;

import com.djomkam.catalogsense.outbox.ProductEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the search index in sync with the catalog by consuming the product events that the
 * outbox relay publishes.
 *
 * <p>Ordering: messages are keyed by catalogId:sku, so all changes to one product arrive in order
 * on one partition. Duplicates (at-least-once delivery) are harmless because the indexer uses
 * external versioning.</p>
 *
 * <p>Failures are retried by the container's error handler and then sent to the dead-letter topic
 * (see {@link SearchKafkaConfig}), so one bad message never blocks the partition forever.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalogsense.search.enabled", havingValue = "true", matchIfMissing = true)
public class ProductEventConsumer {

    private final ProductIndexer indexer;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            id = "search-indexer",
            topics = "${catalogsense.outbox.topic}",
            groupId = "${catalogsense.search.consumer.group-id:catalogsense-search-indexer}",
            autoStartup = "${catalogsense.search.consumer.enabled:true}")
    public void on(ConsumerRecord<String, String> record) throws JsonProcessingException {
        ProductEventMessage event = objectMapper.readValue(record.value(), ProductEventMessage.class);
        switch (event.type()) {
            case ProductEventMessage.UPSERTED -> {
                if (event.product() == null) {
                    throw new IllegalArgumentException("Upsert event " + event.eventId() + " has no product");
                }
                indexer.upsert(ProductDocument.from(event.product()));
            }
            case ProductEventMessage.DELETED -> indexer.delete(event.catalogId(), event.sku());
            default -> log.warn("Ignoring unknown event type {} ({})", event.type(), event.eventId());
        }
    }
}
