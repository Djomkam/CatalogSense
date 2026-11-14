package com.djomkam.catalogsense.ingestion;

import com.djomkam.catalogsense.catalog.Product;
import com.djomkam.catalogsense.catalog.ProductRepository;
import com.djomkam.catalogsense.event.ProductSnapshot;
import com.djomkam.catalogsense.event.ProductsDeletedEvent;
import com.djomkam.catalogsense.event.ProductsUpsertedEvent;
import com.djomkam.catalogsense.health.HealthIssue;
import com.djomkam.catalogsense.health.HealthIssueRepository;
import com.djomkam.catalogsense.ingestion.validation.ValidationRule;
import com.djomkam.catalogsense.ingestion.validation.Violation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applies a batch of product changes to a catalog.
 *
 * <ol>
 *   <li>Normalizes the payloads, then builds the batch context (duplicate SKUs, stored versions)
 *       with one query.</li>
 *   <li>Evaluates every payload with the pure {@link ProductEvaluator}.</li>
 *   <li>Applies the decisions with set-based writes and stores the violations as health issues.</li>
 *   <li>Publishes domain events. They are handled in two ways: the outbox writer stores them
 *       in the same transaction (so Kafka only sees committed changes), and in-process listeners
 *       use {@code @TransactionalEventListener(AFTER_COMMIT)}.</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionService {

    /** Keeps IN lists well below database parameter limits when deleting many SKUs. */
    static final int DELETE_CHUNK_SIZE = 1_000;

    private final ProductRepository productRepository;
    private final HealthIssueRepository healthIssueRepository;
    private final List<ValidationRule> rules;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public IngestionReport ingest(String catalogId, List<ProductPayload> batch, IngestionMode mode) {
        Instant now = clock.instant();
        List<ProductPayload> payloads = batch.stream().map(ProductPayload::normalized).toList();

        // 1. Batch context
        Map<String, Long> occurrencesBySku = payloads.stream()
                .map(ProductPayload::sku)
                .filter(sku -> sku != null && !sku.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Map<String, Product> existingProducts = productRepository
                .findByCatalogIdAndSkuIn(catalogId, occurrencesBySku.keySet()).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));
        Map<String, Long> storedVersions = existingProducts.values().stream()
                .collect(Collectors.toUnmodifiableMap(Product::getSku, Product::getVersion));
        BatchContext context = BatchContext.of(catalogId, occurrencesBySku, storedVersions, now);

        // 2. Pure evaluation: no entity is touched here
        List<ProductOutcome> outcomes = payloads.stream()
                .map(payload -> ProductEvaluator.evaluate(payload, context, rules))
                .toList();

        // 3. Apply decisions
        List<Product> savedProducts = applyOutcomes(catalogId, outcomes, existingProducts, now);
        Set<String> deletedSkus = mode == IngestionMode.FULL
                ? deleteProductsAbsentFrom(catalogId, occurrencesBySku.keySet())
                : Set.of();

        // 4. Report
        IngestionReport report = buildReport(catalogId, mode, batch.size(), outcomes, deletedSkus.size());
        log.info("Ingested catalog={} mode={} received={} accepted={} rejected={} stale={} deleted={}",
                report.catalogId(), report.mode(), report.received(),
                report.accepted(), report.rejected(), report.skippedStale(), report.deleted());

        // 5. Domain events, with snapshots taken while the persistence context is still open
        eventPublisher.publishEvent(new CatalogIngestedEvent(catalogId, mode, report, now));
        if (!savedProducts.isEmpty()) {
            List<ProductSnapshot> snapshots = savedProducts.stream().map(ProductSnapshot::of).toList();
            eventPublisher.publishEvent(new ProductsUpsertedEvent(catalogId, snapshots, now));
        }
        if (!deletedSkus.isEmpty()) {
            eventPublisher.publishEvent(new ProductsDeletedEvent(catalogId, deletedSkus, now));
        }
        return report;
    }

    private List<Product> applyOutcomes(String catalogId, List<ProductOutcome> outcomes,
                                        Map<String, Product> existingProducts, Instant now) {
        List<Product> productsToSave = new ArrayList<>();
        // One entry per SKU: a duplicated SKU is reported for each occurrence but recorded once.
        Map<String, List<Violation>> violationsBySku = new LinkedHashMap<>();

        for (ProductOutcome outcome : outcomes) {
            switch (outcome) {
                case ProductOutcome.Accepted accepted -> {
                    Product existing = existingProducts.get(accepted.sku());
                    var productData = accepted.payload().toProductData();
                    if (existing != null) {
                        existing.updateWith(productData, now);
                        productsToSave.add(existing);
                    } else {
                        productsToSave.add(Product.create(catalogId, accepted.sku(), productData, now));
                    }
                    violationsBySku.put(accepted.sku(), accepted.warnings());
                }
                case ProductOutcome.Rejected rejected -> {
                    if (!rejected.violationsToRecord().isEmpty()) {
                        violationsBySku.putIfAbsent(rejected.sku(), rejected.violationsToRecord());
                    }
                }
                case ProductOutcome.Stale stale -> {
                    // Stale records change nothing, including their issues.
                }
            }
        }

        if (!violationsBySku.isEmpty()) {
            // Replace the issues of every SKU we evaluated: fixed problems disappear, new ones appear.
            healthIssueRepository.deleteByCatalogIdAndSkuIn(catalogId, violationsBySku.keySet());
            List<HealthIssue> issues = new ArrayList<>();
            violationsBySku.forEach((sku, violations) -> violations.forEach(violation ->
                    issues.add(toHealthIssue(catalogId, sku, violation, now))));
            healthIssueRepository.saveAll(issues);
        }
        return productsToSave.isEmpty() ? List.of() : productRepository.saveAll(productsToSave);
    }

    private static HealthIssue toHealthIssue(String catalogId, String sku, Violation violation, Instant detectedAt) {
        return new HealthIssue(catalogId, sku, violation.rule().name(), violation.severity(), violation.field(),
                violation.message(), detectedAt);
    }

    /** Full load: deletes the stored products whose SKU is not in the payload, with their issues. */
    private Set<String> deleteProductsAbsentFrom(String catalogId, Set<String> skusInPayload) {
        List<String> skusToDelete = productRepository.findSkusByCatalogId(catalogId).stream()
                .filter(sku -> !skusInPayload.contains(sku))
                .toList();
        for (int start = 0; start < skusToDelete.size(); start += DELETE_CHUNK_SIZE) {
            List<String> chunk = skusToDelete.subList(start, Math.min(start + DELETE_CHUNK_SIZE, skusToDelete.size()));
            productRepository.deleteByCatalogIdAndSkuIn(catalogId, chunk);
            healthIssueRepository.deleteByCatalogIdAndSkuIn(catalogId, chunk);
        }
        return Set.copyOf(skusToDelete);
    }

    private static IngestionReport buildReport(String catalogId, IngestionMode mode, int receivedCount,
                                               List<ProductOutcome> outcomes, int deletedCount) {
        int accepted = 0;
        int rejected = 0;
        int skippedStale = 0;
        int acceptedWithWarnings = 0;
        List<IngestionReport.Rejection> rejections = new ArrayList<>();

        for (ProductOutcome outcome : outcomes) {
            switch (outcome) {
                case ProductOutcome.Accepted acceptedOutcome -> {
                    accepted++;
                    if (acceptedOutcome.hasWarnings()) {
                        acceptedWithWarnings++;
                    }
                }
                case ProductOutcome.Rejected rejectedOutcome -> {
                    rejected++;
                    rejections.add(new IngestionReport.Rejection(rejectedOutcome.sku(), rejectedOutcome.reasons()));
                }
                case ProductOutcome.Stale staleOutcome -> skippedStale++;
            }
        }
        return new IngestionReport(catalogId, mode, receivedCount, accepted, rejected, skippedStale, deletedCount,
                acceptedWithWarnings, rejections);
    }
}
