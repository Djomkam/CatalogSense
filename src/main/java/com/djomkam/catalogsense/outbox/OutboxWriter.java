package com.djomkam.catalogsense.outbox;

import com.djomkam.catalogsense.event.ProductsDeletedEvent;
import com.djomkam.catalogsense.event.ProductsUpsertedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
    public void on(ProductsUpsertedEvent event) {
        outboxEventRepository.saveAll(event.products().stream()
                .map(p -> toRow(new ProductEventMessage(UUID.randomUUID().toString(), ProductEventMessage.UPSERTED,
                        p.catalogId(), p.sku(), p.version(), event.occurredAt(), p)))
                .toList());
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(ProductsDeletedEvent event) {
        List<OutboxEvent> rows = event.skus().stream().sorted()
                .map(sku -> toRow(new ProductEventMessage(UUID.randomUUID().toString(), ProductEventMessage.DELETED,
                        event.catalogId(), sku, null, event.occurredAt(), null)))
                .toList();
        outboxEventRepository.saveAll(rows);
    }

    private OutboxEvent toRow(ProductEventMessage message) {
        try {
            return new OutboxEvent(message.eventId(), message.type(), message.catalogId(),
                    ProductEventMessage.key(message.catalogId(), message.sku()),
                    objectMapper.writeValueAsString(message), message.occurredAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize outbox event " + message.eventId(), e);
        }
    }
}
