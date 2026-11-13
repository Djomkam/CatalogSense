package com.djomkam.catalogsense.listener;

import com.djomkam.catalogsense.api.IngestionReport;
import com.djomkam.catalogsense.event.CatalogIngestedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class IngestionMetricsListener {

    private final MeterRegistry meterRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CatalogIngestedEvent event) {
        IngestionReport r = event.report();
        String catalog = event.catalogId();
        String mode = event.mode().name();
        meterRegistry.counter("catalogsense.ingestions", "catalog", catalog, "mode", mode).increment();
        meterRegistry.counter("catalogsense.products", "catalog", catalog, "outcome", "accepted").increment(r.accepted());
        meterRegistry.counter("catalogsense.products", "catalog", catalog, "outcome", "rejected").increment(r.rejected());
        meterRegistry.counter("catalogsense.products", "catalog", catalog, "outcome", "stale").increment(r.skippedStale());
        meterRegistry.counter("catalogsense.products", "catalog", catalog, "outcome", "deleted").increment(r.deleted());
    }
}
