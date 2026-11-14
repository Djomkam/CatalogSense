package com.djomkam.catalogsense.search.query;

import com.djomkam.catalogsense.search.FakeOpenSearch;
import com.djomkam.catalogsense.search.FakeOpenSearch.Response;
import com.djomkam.catalogsense.search.OpenSearchTestSupport;
import com.djomkam.catalogsense.search.SearchUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Response parsing against a fake OpenSearch endpoint, using the real Java client (typed aggregation keys). */
class SearchServiceTest {

    private FakeOpenSearch fakeOpenSearch;
    private OpenSearchClient client;
    private SearchService searchService;

    @BeforeEach
    void setUp() throws Exception {
        fakeOpenSearch = new FakeOpenSearch();
        var properties = OpenSearchTestSupport.properties(fakeOpenSearch.url(), "products");
        client = OpenSearchTestSupport.client(properties);
        searchService = new SearchService(client, properties);
    }

    @AfterEach
    void tearDown() {
        OpenSearchTestSupport.close(client);
        fakeOpenSearch.close();
    }

    @Test
    void parsesHitsFacetsAndPriceRange() {
        fakeOpenSearch.respond(request -> new Response(200, """
            {"took":3,"timed_out":false,"_shards":{"total":1,"successful":1,"skipped":0,"failed":0},
             "hits":{"total":{"value":2,"relation":"eq"},"max_score":2.5,"hits":[
               {"_index":"products-v1","_id":"demo:A","_score":2.5,"_source":{"catalogId":"demo","sku":"A","name":"Trail Jacket",
                 "brand":"Acme","category":"Jackets","price":129.99,"currency":"CAD","inStock":true,"version":2,
                 "updatedAt":"2026-11-13T00:00:00Z","colors":["Red"],"sizes":["M"]}}]},
             "aggregations":{
               "filter#brand":{"doc_count":2,"sterms#values":{"doc_count_error_upper_bound":0,"sum_other_doc_count":0,
                 "buckets":[{"key":"Acme","doc_count":1},{"key":"Zest","doc_count":1}]}},
               "filter#color":{"doc_count":1,"sterms#values":{"doc_count_error_upper_bound":0,"sum_other_doc_count":0,
                 "buckets":[{"key":"Red","doc_count":1}]}},
               "filter#price_range":{"doc_count":2,"stats#stats":{"count":2,"min":49.5,"max":129.99,"avg":89.745,"sum":179.49}}}}
            """));

        SearchResult result = searchService.search(ProductSearchCriteria.builder()
                .catalogId("demo").text("jacket").brands(List.of("Acme")).build());

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.hits()).singleElement().satisfies(hit -> {
            assertThat(hit.sku()).isEqualTo("A");
            assertThat(hit.score()).isEqualTo(2.5);
            assertThat(hit.price()).isEqualByComparingTo("129.99");
        });
        assertThat(result.facets().get("brand")).containsExactly(
                new SearchResult.FacetValue("Acme", 1), new SearchResult.FacetValue("Zest", 1));
        assertThat(result.facets().get("color")).containsExactly(new SearchResult.FacetValue("Red", 1));
        assertThat(result.priceRange()).isEqualTo(
                new SearchResult.PriceRange(new BigDecimal("49.50"), new BigDecimal("129.99")));

        FakeOpenSearch.Request search = fakeOpenSearch.requests().getFirst();
        assertThat(search.uri()).startsWith("/products/_search").contains("typed_keys=true");
        assertThat(search.body()).contains("\"post_filter\"", "\"fuzziness\":\"AUTO\"");
    }

    @Test
    void anOpenSearchErrorBecomesSearchUnavailable() {
        fakeOpenSearch.respond(request -> new Response(500, """
                {"error":{"root_cause":[{"type":"exception","reason":"boom"}],"type":"exception","reason":"boom"},"status":500}"""));

        assertThatThrownBy(() -> searchService.search(ProductSearchCriteria.builder().catalogId("demo").build()))
                .isInstanceOf(SearchUnavailableException.class);
    }
}
