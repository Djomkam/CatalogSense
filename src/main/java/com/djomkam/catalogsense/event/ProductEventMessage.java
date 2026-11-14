package com.djomkam.catalogsense.event;

import java.time.Instant;
import java.util.UUID;

/**
 * JSON body of each Kafka message on the product-events topic (the contract shared by the outbox
 * relay and every consumer).
 *
 * @param eventId unique per event; consumers can de-duplicate on it
 * @param version product version (null for deletions); consumers drop events older than what they hold
 * @param product full snapshot for upserts, null for deletions
 */
public record ProductEventMessage(
        String eventId,
        ProductEventType type,
        String catalogId,
        String sku,
        Long version,
        Instant occurredAt,
        ProductSnapshot product
) {

    /** Kafka header names set by the relay. */
    public static final String EVENT_TYPE_HEADER = "eventType";
    public static final String EVENT_ID_HEADER = "eventId";

    public static ProductEventMessage upserted(ProductSnapshot product, Instant occurredAt) {
        return new ProductEventMessage(newEventId(), ProductEventType.UPSERTED, product.catalogId(), product.sku(),
                product.version(), occurredAt, product);
    }

    public static ProductEventMessage deleted(String catalogId, String sku, Instant occurredAt) {
        return new ProductEventMessage(newEventId(), ProductEventType.DELETED, catalogId, sku, null, occurredAt, null);
    }

    /** Kafka key: every change of one product goes to the same partition, so it stays in order. */
    public static String key(String catalogId, String sku) {
        return catalogId + ":" + sku;
    }

    public String key() {
        return key(catalogId, sku);
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }
}
