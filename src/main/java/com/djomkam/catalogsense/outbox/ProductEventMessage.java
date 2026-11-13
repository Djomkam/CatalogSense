package com.djomkam.catalogsense.outbox;

import com.djomkam.catalogsense.event.ProductSnapshot;

import java.time.Instant;

/**
 * JSON body of each Kafka message on the product-events topic.
 *
 * @param eventId    unique per event; consumers de-duplicate on it
 * @param type       {@link #UPSERTED} or {@link #DELETED}
 * @param version    product version (null for deletions); consumers drop events older than what they hold
 * @param product    full snapshot for upserts, null for deletions
 */
public record ProductEventMessage(
        String eventId,
        String type,
        String catalogId,
        String sku,
        Long version,
        Instant occurredAt,
        ProductSnapshot product
) {
    public static final String UPSERTED = "ProductUpserted";
    public static final String DELETED = "ProductDeleted";

    public static String key(String catalogId, String sku) {
        return catalogId + ":" + sku;
    }
}
