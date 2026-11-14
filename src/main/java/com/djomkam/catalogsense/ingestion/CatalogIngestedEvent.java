package com.djomkam.catalogsense.ingestion;

import java.time.Instant;
import java.util.Objects;

/**
 * Emitted after a catalog ingestion completes. In-process only (metrics); it lives with ingestion
 * because it carries the ingestion report.
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
