package com.djomkam.catalogsense.event;

import com.djomkam.catalogsense.api.IngestionReport;
import com.djomkam.catalogsense.service.IngestionMode;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain event emitted after catalog ingestion completes.
 */
public record CatalogIngestedEvent(
        String catalogId,
        IngestionMode mode,
        IngestionReport report,
        Instant occurredAt
) {
    public CatalogIngestedEvent {
        Objects.requireNonNull(catalogId, "catalogId must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(report, "report must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
