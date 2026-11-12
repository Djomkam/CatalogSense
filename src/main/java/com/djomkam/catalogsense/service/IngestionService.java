package com.djomkam.catalogsense.service;

import com.djomkam.catalogsense.api.IngestionReport;
import com.djomkam.catalogsense.api.ProductPayload;
import com.djomkam.catalogsense.domain.HealthIssue;
import com.djomkam.catalogsense.domain.HealthIssueRepository;
import com.djomkam.catalogsense.domain.Product;
import com.djomkam.catalogsense.domain.ProductRepository;
import com.djomkam.catalogsense.event.CatalogIngestedEvent;
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
 * Coordinates catalog ingestion through a pure functional evaluation pipeline and set-based persistence.
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

        // 1. Prepare Batch Context
        Map<String, Long> skuFrequency = batch.stream()
                .map(ProductPayload::sku)
                .filter(sku -> sku != null && !sku.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Map<String, Product> existingProducts = productRepository
                .findByCatalogIdAndSkuIn(catalogId, skuFrequency.keySet()).stream()
                .collect(Collectors.toMap(Product::getSku, Function.identity()));

        // 2. Pure Functional Evaluation Pipeline
        Set<String> firstSeenDuplicates = new HashSet<>();
        List<ProductOutcome> outcomes = batch.stream()
                .map(payload -> ProductEvaluator.evaluate(
                        payload, catalogId, skuFrequency, existingProducts, rules, now, firstSeenDuplicates))
                .toList();

        // 3. Set-Based Bulk Persistence
        List<Product> savedProducts = persistOutcomes(catalogId, outcomes);

        Set<String> deletedSkus = (mode == IngestionMode.FULL)
                ? deleteMissing(catalogId, skuFrequency.keySet())
                : Set.of();

        // 4. Report Assembly
        IngestionReport report = buildReport(catalogId, mode, batch.size(), outcomes, deletedSkus.size());

        log.info("Ingested catalog={} mode={} received={} accepted={} rejected={} stale={} deleted={}",
                report.catalogId(), report.mode(), report.received(),
                report.accepted(), report.rejected(), report.skippedStale(), report.deleted());

        // 5. Emit Domain Events
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new CatalogIngestedEvent(catalogId, mode, report, now));
            if (!savedProducts.isEmpty()) {
                eventPublisher.publishEvent(new ProductsUpsertedEvent(catalogId, savedProducts, now));
            }
            if (!deletedSkus.isEmpty()) {
                eventPublisher.publishEvent(new ProductsDeletedEvent(catalogId, deletedSkus, now));
            }
        }

        return report;
    }

    private List<Product> persistOutcomes(String catalogId, List<ProductOutcome> outcomes) {
        List<Product> productsToSave = new ArrayList<>();
        List<HealthIssue> issuesToSave = new ArrayList<>();
        Set<String> skusToClearIssues = new HashSet<>();

        for (ProductOutcome outcome : outcomes) {
            switch (outcome) {
                case ProductOutcome.Accepted accepted -> {
                    productsToSave.add(accepted.product());
                    skusToClearIssues.add(accepted.product().getSku());
                    issuesToSave.addAll(accepted.issues());
                }
                case ProductOutcome.Rejected rejected -> {
                    if (!rejected.issuesToRecord().isEmpty()) {
                        skusToClearIssues.add(rejected.sku());
                        issuesToSave.addAll(rejected.issuesToRecord());
                    }
                }
                case ProductOutcome.Stale stale -> {
                    // Stale records produce no updates or issue changes
                }
            }
        }

        if (!skusToClearIssues.isEmpty()) {
            healthIssueRepository.deleteByCatalogIdAndSkuIn(catalogId, skusToClearIssues);
        }
        if (!productsToSave.isEmpty()) {
            productRepository.saveAll(productsToSave);
        }
        if (!issuesToSave.isEmpty()) {
            healthIssueRepository.saveAll(issuesToSave);
        }

        return productsToSave;
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
