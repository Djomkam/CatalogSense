package com.djomkam.catalogsense.event;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Domain event emitted when products are removed from a catalog.
 */
public record ProductsDeletedEvent(
        String catalogId,
        Set<String> skus,
        Instant occurredAt
) {
    public ProductsDeletedEvent {
        Objects.requireNonNull(catalogId, "catalogId must not be null");
        Objects.requireNonNull(skus, "skus must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        skus = Set.copyOf(skus);
    }
}
