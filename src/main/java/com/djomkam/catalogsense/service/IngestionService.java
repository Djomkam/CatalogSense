package com.djomkam.catalogsense.service;

import com.djomkam.catalogsense.api.IngestionReport;
import com.djomkam.catalogsense.api.ProductPayload;
import com.djomkam.catalogsense.domain.HealthIssue;
import com.djomkam.catalogsense.domain.HealthIssueRepository;
import com.djomkam.catalogsense.domain.Product;
import com.djomkam.catalogsense.domain.ProductRepository;
import com.djomkam.catalogsense.event.CatalogIngestedEvent;
import com.djomkam.catalogsense.event.ProductSnapshot;
import com.djomkam.catalogsense.event.ProductsDeletedEvent;
import com.djomkam.catalogsense.event.ProductsUpsertedEvent;
import com.djomkam.catalogsense.validation.ValidationRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Applies a batch of product changes to a catalog.
 *
 * <ol>
 *   <li>Builds the batch context (duplicate SKUs, stored versions) with one query.</li>
 *   <li>Evaluates every payload with the pure {@link ProductEvaluator}.</li>
 *   <li>Applies the decisions with set-based writes.</li>
 *   <li>Publishes domain events. They are handled in two ways: the outbox writer stores them
 *       in the same transaction (so Kafka only sees committed changes), and in-process listeners
 *       use {@code @TransactionalEventListener(AFTER_COMMIT)}.</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionService {

    private final ProductRepository productRepository;
    private final HealthIssueRepository healthIssueRepository;
    private final List<ValidationRule> rules;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public IngestionReport ingest(String catalogId, List<ProductPayload> batch, IngestionMode mode) {
        Instant now = clock.instant();

        // 1. Batch context
        Map<String, Long> skuFrequency = batch.stream()
                .map(ProductPayload::sku)
                .filter(sku -> sku != null && !sku.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Map<String, Long> duplicateSkus = skuFrequency.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<String, Product> existingProducts = productRepository
                .findByCatalogIdAndSkuIn(catalogId, skuFrequency.keySet()).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));

        Map<String, Long> existingVersions = existingProducts.values().stream()
                .collect(Collectors.toUnmodifiableMap(Product::getSku, Product::getVersion));

        // 2. Pure evaluation: no entity is touched here
        List<ProductOutcome> outcomes = batch.stream()
                .map(payload -> ProductEvaluator.evaluate(
                        payload, catalogId, duplicateSkus, existingVersions, rules, now))
                .toList();

        // 3. Apply decisions
        List<Product> savedProducts = persistOutcomes(catalogId, outcomes, existingProducts, now);

        Set<String> deletedSkus = (mode == IngestionMode.FULL)
                ? deleteMissing(catalogId, skuFrequency.keySet())
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

    private List<Product> persistOutcomes(String catalogId, List<ProductOutcome> outcomes,
                                          Map<String, Product> existingProducts, Instant now) {
        List<Product> productsToSave = new ArrayList<>();
        // One entry per SKU: a duplicated SKU is reported for each occurrence but recorded once.
        Map<String, List<HealthIssue>> issuesBySku = new LinkedHashMap<>();

        for (ProductOutcome outcome : outcomes) {
            switch (outcome) {
                case ProductOutcome.Accepted accepted -> {
                    Product existing = existingProducts.get(accepted.sku());
                    Product product;
                    if (existing != null) {
                        existing.updateFromPayload(accepted.payload(), now);
                        product = existing;
                    } else {
                        product = Product.create(catalogId, accepted.sku(), accepted.payload(), now);
                    }
                    productsToSave.add(product);
                    issuesBySku.put(accepted.sku(), accepted.issues());
                }
                case ProductOutcome.Rejected rejected -> {
                    if (!rejected.issuesToRecord().isEmpty()) {
                        issuesBySku.putIfAbsent(rejected.sku(), rejected.issuesToRecord());
                    }
                }
                case ProductOutcome.Stale stale -> {
                    // Stale records change nothing, including their issues.
                }
            }
        }

        if (!issuesBySku.isEmpty()) {
            healthIssueRepository.deleteByCatalogIdAndSkuIn(catalogId, issuesBySku.keySet());
            healthIssueRepository.saveAll(issuesBySku.values().stream().flatMap(List::stream).toList());
        }
        return productsToSave.isEmpty() ? List.of() : productRepository.saveAll(productsToSave);
    }

    private Set<String> deleteMissing(String catalogId, Set<String> skusInPayload) {
        List<String> toDelete = productRepository.findSkusByCatalogId(catalogId).stream()
                .filter(sku -> !skusInPayload.contains(sku))
                .toList();

        if (toDelete.isEmpty()) {
            return Set.of();
        }

        productRepository.deleteAll(productRepository.findByCatalogIdAndSkuIn(catalogId, toDelete));
        healthIssueRepository.deleteByCatalogIdAndSkuIn(catalogId, toDelete);
        return Set.copyOf(toDelete);
    }

    private IngestionReport buildReport(String catalogId, IngestionMode mode, int receivedCount,
                                        List<ProductOutcome> outcomes, int deletedCount) {
        int accepted = 0;
        int rejected = 0;
        int skippedStale = 0;
        int warnings = 0;
        List<IngestionReport.Rejection> rejections = new ArrayList<>();

        for (ProductOutcome outcome : outcomes) {
            switch (outcome) {
                case ProductOutcome.Accepted a -> {
                    accepted++;
                    if (a.hasWarnings()) {
                        warnings++;
                    }
                }
                case ProductOutcome.Rejected r -> {
                    rejected++;
                    rejections.add(new IngestionReport.Rejection(r.sku(), r.reasons()));
                }
                case ProductOutcome.Stale s -> skippedStale++;
            }
        }

        return new IngestionReport(
                catalogId, mode.name(), receivedCount, accepted, rejected, skippedStale, deletedCount, warnings, rejections);
    }
}
