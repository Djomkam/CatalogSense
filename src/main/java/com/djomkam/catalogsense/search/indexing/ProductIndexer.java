package com.djomkam.catalogsense.search.indexing;

import com.djomkam.catalogsense.search.ConditionalOnSearchEnabled;
import com.djomkam.catalogsense.search.OpenSearchErrors;
import com.djomkam.catalogsense.search.ProductDocument;
import com.djomkam.catalogsense.search.SearchUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.VersionType;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Writes products to OpenSearch.
 *
 * <p>Documents are indexed with <b>external versioning</b>: OpenSearch stores the product's own
 * version and refuses a write whose version is not higher. A redelivered or late Kafka message
 * can therefore never overwrite newer data in the index, which makes the consumer idempotent
 * without keeping a table of processed event ids.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnSearchEnabled
public class ProductIndexer {

    private final OpenSearchClient client;
    private final ProductIndexManager indexManager;

    /**
     * Indexes one product through the alias. A version conflict means OpenSearch already holds this
     * version or a newer one (a duplicate or late message); that is expected and skipped.
     */
    public void upsert(ProductDocument document) {
        indexManager.requireIndex();
        try {
            client.index(request -> request
                    .index(indexManager.alias())
                    .id(document.documentId())
                    .document(document)
                    .versionType(VersionType.External)
                    .version(document.version()));
        } catch (OpenSearchException | IOException e) {
            if (!OpenSearchErrors.isVersionConflict(e)) {
                throw new SearchUnavailableException("Could not index " + document.documentId(), e);
            }
            log.debug("Skipped {} v{}: the index already has this version or newer",
                    document.documentId(), document.version());
        }
    }

    /** Deletes one product through the alias. A product that is already gone is not an error. */
    public void delete(String catalogId, String sku) {
        indexManager.requireIndex();
        String documentId = ProductDocument.documentId(catalogId, sku);
        try {
            client.delete(request -> request.index(indexManager.alias()).id(documentId));
        } catch (OpenSearchException | IOException e) {
            if (!OpenSearchErrors.isNotFound(e)) {
                throw new SearchUnavailableException("Could not delete " + documentId, e);
            }
        }
    }

    /**
     * Writes a page of products into one physical index (used by the reindex, which fills a fresh index
     * that is not behind the alias yet).
     *
     * @return number of documents OpenSearch rejected
     */
    public int bulkIndex(String index, List<ProductDocument> documents) {
        if (documents.isEmpty()) {
            return 0;
        }
        List<BulkOperation> operations = documents.stream()
                .map(document -> BulkOperation.of(operation -> operation.index(write -> write
                        .index(index)
                        .id(document.documentId())
                        .document(document)
                        .versionType(VersionType.External)
                        .version(document.version()))))
                .toList();
        try {
            BulkResponse response = client.bulk(request -> request.operations(operations));
            int rejected = (int) response.items().stream().filter(item -> item.error() != null).count();
            if (rejected > 0) {
                log.warn("Bulk index into {}: {} of {} documents rejected", index, rejected, documents.size());
            }
            return rejected;
        } catch (OpenSearchException | IOException e) {
            throw new SearchUnavailableException("Bulk index into " + index + " failed", e);
        }
    }
}
