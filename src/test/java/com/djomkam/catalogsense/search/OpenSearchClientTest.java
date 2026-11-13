package com.djomkam.catalogsense.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Indexer and search service against a fake OpenSearch HTTP endpoint, using the real Java client. */
class OpenSearchClientTest {

    private static final String SHARDS = "\"_shards\":{\"total\":1,\"successful\":1,\"failed\":0}";

    private FakeOpenSearch fake;
    private ProductIndexer indexer;
    private SearchService searchService;

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeOpenSearch();
        var properties = new SearchProperties(true, fake.url(), "products",
                new SearchProperties.Consumer("g", 0, java.time.Duration.ZERO));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        OpenSearchClient client = new OpenSearchConfig().openSearchClient(properties, mapper);
        indexer = new ProductIndexer(client, properties);
        searchService = new SearchService(client, properties);
    }

    @AfterEach
    void tearDown() {
        fake.close();
    }

    private static ProductDocument doc(String sku, long version) {
        return new ProductDocument("demo", sku, "Trail Jacket", "Acme", "Jackets", new BigDecimal("129.99"), "CAD",
                null, true, version, Instant.parse("2026-11-13T00:00:00Z"), List.of("Red"), List.of("M"));
    }

    @Test
    void createsTheIndexWithItsMappingWhenMissing() {
        fake.respond(r -> r.method().equals("HEAD")
                ? new FakeOpenSearch.Response(404, "")
                : new FakeOpenSearch.Response(200, "{\"acknowledged\":true,\"shards_acknowledged\":true,\"index\":\"products\"}"));

        indexer.ensureIndex();
        indexer.ensureIndex(); // second call is a no-op

        assertThat(fake.requests()).extracting(FakeOpenSearch.Request::method).containsExactly("HEAD", "PUT");
        String body = fake.requests().get(1).body();
        assertThat(body).contains("\"dynamic\":\"strict\"", "\"asciifolding\"", "\"scaled_float\"", "\"colors\"");
    }

    @Test
    void upsertUsesExternalVersioningAndTreatsConflictsAsStale() {
        fake.respond(r -> switch (r.method()) {
            case "HEAD" -> new FakeOpenSearch.Response(200, "");
            default -> r.uri().contains("version=1&") || r.uri().endsWith("version=1")
                    ? new FakeOpenSearch.Response(409, """
                        {"error":{"root_cause":[{"type":"version_conflict_engine_exception","reason":"conflict"}],
                        "type":"version_conflict_engine_exception","reason":"conflict"},"status":409}""")
                    : new FakeOpenSearch.Response(201, """
                        {"_index":"products","_id":"demo:A","_version":2,"result":"created",%s,"_seq_no":0,"_primary_term":1}"""
                        .formatted(SHARDS));
        });

        assertThat(indexer.upsert(doc("A", 2))).isTrue();
        assertThat(indexer.upsert(doc("A", 1))).isFalse();

        FakeOpenSearch.Request put = fake.requests().get(1);
        assertThat(put.method()).isEqualTo("PUT");
        assertThat(put.uri()).matches("/products/_doc/demo(:|%3A)A\\?.*").contains("version_type=external", "version=2");
        assertThat(put.body()).contains("\"updatedAt\":\"2026-11-13T00:00:00Z\"", "\"colors\":[\"Red\"]");
    }

    @Test
    void otherIndexingErrorsPropagateSoTheConsumerRetries() {
        fake.respond(r -> r.method().equals("HEAD")
                ? new FakeOpenSearch.Response(200, "")
                : new FakeOpenSearch.Response(503, """
                    {"error":{"root_cause":[{"type":"cluster_block_exception","reason":"blocked"}],
                    "type":"cluster_block_exception","reason":"blocked"},"status":503}"""));

        assertThatThrownBy(() -> indexer.upsert(doc("A", 2))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void deletingAMissingProductIsNotAnError() {
        fake.respond(r -> r.method().equals("HEAD")
                ? new FakeOpenSearch.Response(200, "")
                : new FakeOpenSearch.Response(404, """
                    {"_index":"products","_id":"demo:GONE","_version":1,"result":"not_found",%s,"_seq_no":1,"_primary_term":1}"""
                    .formatted(SHARDS)));

        indexer.delete("demo", "GONE");

        assertThat(fake.requests().get(1).method()).isEqualTo("DELETE");
    }

    @Test
    void parsesHitsFacetsAndPriceRange() {
        fake.respond(r -> new FakeOpenSearch.Response(200, """
            {"took":3,"timed_out":false,"_shards":{"total":1,"successful":1,"skipped":0,"failed":0},
             "hits":{"total":{"value":2,"relation":"eq"},"max_score":2.5,"hits":[
               {"_index":"products","_id":"demo:A","_score":2.5,"_source":{"catalogId":"demo","sku":"A","name":"Trail Jacket",
                 "brand":"Acme","category":"Jackets","price":129.99,"currency":"CAD","inStock":true,"version":2,
                 "updatedAt":"2026-11-13T00:00:00Z","colors":["Red"],"sizes":["M"]}}]},
             "aggregations":{
               "filter#brand":{"doc_count":2,"sterms#values":{"doc_count_error_upper_bound":0,"sum_other_doc_count":0,
                 "buckets":[{"key":"Acme","doc_count":1},{"key":"Zest","doc_count":1}]}},
               "filter#color":{"doc_count":1,"sterms#values":{"doc_count_error_upper_bound":0,"sum_other_doc_count":0,
                 "buckets":[{"key":"Red","doc_count":1}]}},
               "filter#price":{"doc_count":2,"stats#stats":{"count":2,"min":49.5,"max":129.99,"avg":89.745,"sum":179.49}}}}
            """));

        SearchResult result = searchService.search(new ProductSearchCriteria("demo", "jacket", List.of("Acme"),
                null, null, null, null, null, null, null, 0, 20));

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.hits()).singleElement().satisfies(h -> {
            assertThat(h.sku()).isEqualTo("A");
            assertThat(h.score()).isEqualTo(2.5);
            assertThat(h.price()).isEqualByComparingTo("129.99");
        });
        assertThat(result.facets().get("brand")).containsExactly(
                new SearchResult.FacetValue("Acme", 1), new SearchResult.FacetValue("Zest", 1));
        assertThat(result.facets().get("color")).containsExactly(new SearchResult.FacetValue("Red", 1));
        assertThat(result.priceRange()).isEqualTo(
                new SearchResult.PriceRange(new BigDecimal("49.50"), new BigDecimal("129.99")));

        FakeOpenSearch.Request search = fake.requests().get(0);
        assertThat(search.uri()).startsWith("/products/_search").contains("typed_keys=true");
        assertThat(search.body()).contains("\"post_filter\"", "\"fuzziness\":\"AUTO\"");
    }
}
