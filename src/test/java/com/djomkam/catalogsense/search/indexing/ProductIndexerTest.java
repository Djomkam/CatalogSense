package com.djomkam.catalogsense.search.indexing;

import com.djomkam.catalogsense.search.FakeOpenSearch;
import com.djomkam.catalogsense.search.FakeOpenSearch.Response;
import com.djomkam.catalogsense.search.OpenSearchTestSupport;
import com.djomkam.catalogsense.search.SearchUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;

import java.util.List;

import static com.djomkam.catalogsense.search.OpenSearchTestSupport.jacket;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Document writes against a fake OpenSearch endpoint, using the real Java client. */
class ProductIndexerTest {

    private static final String SHARDS = "\"_shards\":{\"total\":1,\"successful\":1,\"failed\":0}";
    private static final Response ALIAS_EXISTS = new Response(200, "");

    private FakeOpenSearch fakeOpenSearch;
    private OpenSearchClient client;
    private ProductIndexer indexer;

    @BeforeEach
    void setUp() throws Exception {
        fakeOpenSearch = new FakeOpenSearch();
        var properties = OpenSearchTestSupport.properties(fakeOpenSearch.url(), "products");
        client = OpenSearchTestSupport.client(properties);
        indexer = new ProductIndexer(client, new ProductIndexManager(client, properties));
    }

    @AfterEach
    void tearDown() {
        OpenSearchTestSupport.close(client);
        fakeOpenSearch.close();
    }

    @Test
    void upsertWritesThroughTheAliasWithExternalVersioning() {
        fakeOpenSearch.respond(request -> request.method().equals("HEAD") ? ALIAS_EXISTS : new Response(201, """
                {"_index":"products-v1","_id":"demo:A","_version":2,"result":"created",%s,"_seq_no":0,"_primary_term":1}"""
                .formatted(SHARDS)));

        indexer.upsert(jacket("demo", "A", 2));

        FakeOpenSearch.Request write = fakeOpenSearch.requests().get(1);
        assertThat(write.method()).isEqualTo("PUT");
        assertThat(write.uri()).matches("/products/_doc/demo(:|%3A)A\\?.*").contains("version_type=external", "version=2");
        assertThat(write.body()).contains("\"updatedAt\":\"2026-11-13T00:00:00Z\"", "\"colors\":[\"Red\"]");
    }

    @Test
    void aVersionConflictMeansStaleAndIsSkipped() {
        fakeOpenSearch.respond(request -> request.method().equals("HEAD") ? ALIAS_EXISTS : new Response(409, """
                {"error":{"root_cause":[{"type":"version_conflict_engine_exception","reason":"conflict"}],
                "type":"version_conflict_engine_exception","reason":"conflict"},"status":409}"""));

        assertThatCode(() -> indexer.upsert(jacket("demo", "A", 1))).doesNotThrowAnyException();
    }

    @Test
    void otherErrorsPropagateSoTheConsumerRetries() {
        fakeOpenSearch.respond(request -> request.method().equals("HEAD") ? ALIAS_EXISTS : new Response(503, """
                {"error":{"root_cause":[{"type":"cluster_block_exception","reason":"blocked"}],
                "type":"cluster_block_exception","reason":"blocked"},"status":503}"""));

        assertThatThrownBy(() -> indexer.upsert(jacket("demo", "A", 2)))
                .isInstanceOf(SearchUnavailableException.class)
                .hasMessageContaining("demo:A");
    }

    @Test
    void deletingAMissingProductIsNotAnError() {
        fakeOpenSearch.respond(request -> request.method().equals("HEAD") ? ALIAS_EXISTS : new Response(404, """
                {"_index":"products-v1","_id":"demo:GONE","_version":1,"result":"not_found",%s,"_seq_no":1,"_primary_term":1}"""
                .formatted(SHARDS)));

        indexer.delete("demo", "GONE");

        assertThat(fakeOpenSearch.requests().get(1).method()).isEqualTo("DELETE");
    }

    @Test
    void bulkIndexWritesIntoTheGivenIndexAndCountsRejections() {
        fakeOpenSearch.respond(request -> new Response(200, """
                {"took":5,"errors":true,"items":[
                  {"index":{"_index":"products-v2","_id":"demo:A","_version":1,"result":"created",%1$s,"_seq_no":0,"_primary_term":1,"status":201}},
                  {"index":{"_index":"products-v2","_id":"demo:B","status":400,
                    "error":{"type":"strict_dynamic_mapping_exception","reason":"unknown field"}}}]}"""
                .formatted(SHARDS)));

        int rejected = indexer.bulkIndex("products-v2", List.of(jacket("demo", "A", 1), jacket("demo", "B", 1)));

        assertThat(rejected).isEqualTo(1);
        FakeOpenSearch.Request bulk = fakeOpenSearch.requests().getFirst();
        assertThat(bulk.uri()).startsWith("/_bulk");
        assertThat(bulk.body()).contains("\"_index\":\"products-v2\"", "\"version_type\":\"external\"");
    }
}
