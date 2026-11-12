package com.djomkam.catalogsense.api;

import java.util.List;

/**
 * Result of one ingestion call.
 *
 * @param received      products in the request
 * @param accepted      created or updated
 * @param rejected      blocked by at least one ERROR rule
 * @param skippedStale  ignored because their version was not newer than the stored one
 * @param deleted       removed because they were absent from a full load
 * @param warnings      accepted products that still have WARNING issues
 * @param rejections    why each rejected product was rejected
 */
public record IngestionReport(
        String catalogId,
        String mode,
        int received,
        int accepted,
        int rejected,
        int skippedStale,
        int deleted,
        int warnings,
        List<Rejection> rejections) {

    public record Rejection(String sku, List<String> reasons) {
    }
}
