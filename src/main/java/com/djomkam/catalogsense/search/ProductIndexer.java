package com.djomkam.catalogsense.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.VersionType;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.transport.httpclient5.ResponseException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Writes products to the OpenSearch index.
 *
 * <p>Documents are indexed with <b>external versioning</b>: OpenSearch stores the product's own
 * version and refuses a write whose version is not higher. A redelivered or late Kafka message
 * can therefore never overwrite newer data in the index, which makes the consumer idempotent
 * without keeping a table of processed event ids.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalogsense.search.enabled", havingValue = "true", matchIfMissing = true)
public class ProductIndexer {

    private static final String FOLDING = "folding";

    private final OpenSearchClient client;
    private final SearchProperties properties;
    private volatile boolean indexReady;

    /** Creates the index with its mapping if it doesn't exist yet. */
    public void ensureIndex() {
        if (indexReady) {
            return;
        }
        try {
            if (client.indices().exists(e -> e.index(properties.index())).value()) {
                indexReady = true;
                return;
            }
            client.indices().create(c -> c
                    .index(properties.index())
                    .settings(s -> s
                            .numberOfShards("1")
                            .numberOfReplicas("0")
                            // lowercase + asciifolding: "Écharpe" matches "echarpe" (French catalogs)
                            .analysis(a -> a.analyzer(FOLDING, an -> an.custom(cu -> cu
                                    .tokenizer("standard")
                                    .filter("lowercase", "asciifolding")))))
                    .mappings(m -> m
                            .dynamic(org.opensearch.client.opensearch._types.mapping.DynamicMapping.Strict)
                            .properties("catalogId", keyword())
                            .properties("sku", keyword())
                            .properties("name", p -> p.text(t -> t.analyzer(FOLDING)
                                    .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))))
                            .properties("brand", keywordWithText())
                            .properties("category", keywordWithText())
                            .properties("colors", keywordWithText())
                            .properties("sizes", keyword())
                            .properties("price", p -> p.scaledFloat(f -> f.scalingFactor(100.0)))
                            .properties("currency", keyword())
                            .properties("imageUrl", p -> p.keyword(k -> k.index(false)))
                            .properties("inStock", p -> p.boolean_(b -> b))
                            .properties("version", p -> p.long_(l -> l))
                            .properties("updatedAt", p -> p.date(d -> d))));
            log.info("Created OpenSearch index {}", properties.index());
            indexReady = true;
        } catch (OpenSearchException | IOException e) {
            // Another instance created it between our check and our create.
            boolean alreadyExists = e instanceof OpenSearchException ose
                    ? "resource_already_exists_exception".equals(ose.error().type())
                    : status(e) == 400 && e.toString().contains("resource_already_exists_exception");
            if (!alreadyExists) {
                throw rethrow(e);
            }
            indexReady = true;
        }
    }

    /**
     * Indexes one product. Returns false when OpenSearch already holds this version or a newer one
     * (a duplicate or late message), which is expected with at-least-once delivery.
     */
    public boolean upsert(ProductDocument doc) {
        ensureIndex();
        try {
            client.index(i -> i
                    .index(properties.index())
                    .id(doc.id())
                    .document(doc)
                    .versionType(VersionType.External)
                    .version(doc.version()));
            return true;
        } catch (OpenSearchException | IOException e) {
            if (status(e) == 409) {
                log.debug("Skipped {} v{}: index already has this version or newer", doc.id(), doc.version());
                return false;
            }
            throw rethrow(e);
        }
    }

    /** Deletes one product. A product that is already gone is not an error. */
    public void delete(String catalogId, String sku) {
        ensureIndex();
        try {
            client.delete(d -> d.index(properties.index()).id(ProductDocument.id(catalogId, sku)));
        } catch (OpenSearchException | IOException e) {
            if (status(e) != 404) {
                throw rethrow(e);
            }
        }
    }

    /**
     * Bulk-indexes products for a backfill. Uses external_gte so re-running a reindex with the
     * same versions succeeds, while older versions are still refused.
     *
     * @return number of documents that failed
     */
    public int bulkUpsert(List<ProductDocument> docs) {
        if (docs.isEmpty()) {
            return 0;
        }
        ensureIndex();
        List<BulkOperation> ops = docs.stream()
                .map(doc -> BulkOperation.of(o -> o.index(i -> i
                        .index(properties.index())
                        .id(doc.id())
                        .document(doc)
                        .versionType(VersionType.ExternalGte)
                        .version(doc.version()))))
                .toList();
        try {
            BulkResponse response = client.bulk(b -> b.operations(ops));
            int failed = (int) response.items().stream()
                    .filter(item -> item.error() != null && item.status() != 409)
                    .count();
            if (failed > 0) {
                log.warn("Bulk index: {} of {} documents failed", failed, docs.size());
            }
            return failed;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * HTTP status of a failed call. Depending on the response, the client reports an error status
     * either as an OpenSearchException or as a transport ResponseException (an IOException).
     */
    static int status(Exception e) {
        if (e instanceof OpenSearchException ose) {
            return ose.status();
        }
        if (e instanceof ResponseException re) {
            return re.status();
        }
        return -1;
    }

    private static RuntimeException rethrow(Exception e) {
        return e instanceof RuntimeException re ? re : new UncheckedIOException((IOException) e);
    }

    private static java.util.function.Function<Property.Builder, org.opensearch.client.util.ObjectBuilder<Property>> keyword() {
        return p -> p.keyword(k -> k);
    }

    /** Keyword for exact filters and facets, plus a ".text" sub-field for full-text search. */
    private static java.util.function.Function<Property.Builder, org.opensearch.client.util.ObjectBuilder<Property>> keywordWithText() {
        return p -> p.keyword(k -> k.fields("text", f -> f.text(t -> t.analyzer(FOLDING))));
    }
}
