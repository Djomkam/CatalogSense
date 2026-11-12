package com.djomkam.catalogsense.event;

import com.djomkam.catalogsense.domain.Product;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Domain event emitted when products are upserted into a catalog.
 */
public record ProductsUpsertedEvent(
        String catalogId,
        List<Product> products,
        Instant occurredAt
) {
    public ProductsUpsertedEvent {
        Objects.requireNonNull(catalogId, "catalogId must not be null");
        Objects.requireNonNull(products, "products must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        products = List.copyOf(products);
    }
}
