package com.djomkam.catalogsense.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Domain event emitted when products are created or updated in a catalog.
 * Carries immutable {@link ProductSnapshot}s, never JPA entities.
 */
public record ProductsUpsertedEvent(
        String catalogId,
        List<ProductSnapshot> products,
        Instant occurredAt
) {
    public ProductsUpsertedEvent {
        Objects.requireNonNull(catalogId, "catalogId must not be null");
        Objects.requireNonNull(products, "products must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        products = List.copyOf(products);
    }
}
