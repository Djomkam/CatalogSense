package com.djomkam.catalogsense.ingestion;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What the evaluator needs to know about the whole batch to judge one product. Built once per batch.
 *
 * @param duplicateSkuCounts SKUs that appear more than once in the batch, with how often
 * @param storedVersions     version currently stored for each SKU of the batch that already exists
 * @param evaluatedAt        timestamp put on the health issues found
 */
public record BatchContext(
        String catalogId,
        Map<String, Long> duplicateSkuCounts,
        Map<String, Long> storedVersions,
        Instant evaluatedAt
) {

    public BatchContext {
        duplicateSkuCounts = Map.copyOf(duplicateSkuCounts);
        storedVersions = Map.copyOf(storedVersions);
    }

    /** Keeps only the SKUs of {@code occurrencesBySku} that occur more than once. */
    public static BatchContext of(String catalogId, Map<String, Long> occurrencesBySku,
                                  Map<String, Long> storedVersions, Instant evaluatedAt) {
        Map<String, Long> duplicates = occurrencesBySku.entrySet().stream()
                .filter(skuOccurrences -> skuOccurrences.getValue() > 1)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new BatchContext(catalogId, duplicates, storedVersions, evaluatedAt);
    }

    /** True when the stored version is the same or newer, so this payload changes nothing. */
    public boolean isStale(String sku, Long payloadVersion) {
        Long storedVersion = storedVersions.get(sku);
        return storedVersion != null && payloadVersion != null && payloadVersion <= storedVersion;
    }
}
