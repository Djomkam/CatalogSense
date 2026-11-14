package com.djomkam.catalogsense.outbox;

import com.djomkam.catalogsense.event.ProductEventMessage;
import com.djomkam.catalogsense.event.ProductsDeletedEvent;
import com.djomkam.catalogsense.event.ProductsUpsertedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns domain events into outbox rows, inside the ingestion transaction.
 *
 * <p>This is a plain {@code @EventListener} on purpose: it runs synchronously in the
 * publisher's transaction ({@code MANDATORY} fails fast if there is none), so the catalog
 * change and its outbox rows commit or roll back together. That solves the dual-write
 * problem without a distributed transaction.</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onProductsUpserted(ProductsUpsertedEvent event) {
        outboxEventRepository.saveAll(event.products().stream()
                .map(product -> toOutboxEvent(ProductEventMessage.upserted(product, event.occurredAt())))
                .toList());
    }

    /** SKUs are sorted so the rows (and the Kafka messages) come out in a stable order. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onProductsDeleted(ProductsDeletedEvent event) {
        outboxEventRepository.saveAll(event.skus().stream().sorted()
                .map(sku -> toOutboxEvent(ProductEventMessage.deleted(event.catalogId(), sku, event.occurredAt())))
                .toList());
    }

    private OutboxEvent toOutboxEvent(ProductEventMessage message) {
        try {
            return new OutboxEvent(message.eventId(), message.type(), message.catalogId(), message.key(),
                    objectMapper.writeValueAsString(message), message.occurredAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize outbox event " + message.eventId(), e);
        }
    }
}
