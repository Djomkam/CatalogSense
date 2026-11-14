package com.djomkam.catalogsense.ingestion;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Records ingestion metrics, only for batches that were actually committed.
 *
 * <p>{@code AFTER_COMMIT} is the rule for any in-process side effect (metrics, cache eviction,
 * notifications): if the transaction rolls back, the listener never runs.</p>
 */
@Component
@RequiredArgsConstructor
public class IngestionMetricsListener {

    static final String INGESTIONS_METRIC = "catalogsense.ingestions";
    static final String PRODUCTS_METRIC = "catalogsense.products";

    private final MeterRegistry meterRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void recordIngestionMetrics(CatalogIngestedEvent event) {
        IngestionReport report = event.report();
        String catalogId = event.catalogId();
        meterRegistry.counter(INGESTIONS_METRIC, "catalog", catalogId, "mode", event.mode().name()).increment();
        countProducts(catalogId, "accepted", report.accepted());
        countProducts(catalogId, "rejected", report.rejected());
        countProducts(catalogId, "stale", report.skippedStale());
        countProducts(catalogId, "deleted", report.deleted());
    }

    private void countProducts(String catalogId, String outcome, int count) {
        meterRegistry.counter(PRODUCTS_METRIC, "catalog", catalogId, "outcome", outcome).increment(count);
    }
}
