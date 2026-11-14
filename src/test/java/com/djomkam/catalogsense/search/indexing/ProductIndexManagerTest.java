package com.djomkam.catalogsense.search.indexing;

import com.djomkam.catalogsense.search.FakeOpenSearch;
import com.djomkam.catalogsense.search.FakeOpenSearch.Response;
import com.djomkam.catalogsense.search.OpenSearchTestSupport;
import com.djomkam.catalogsense.search.SearchUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Index creation and alias switching against a fake OpenSearch endpoint, using the real Java client. */
class ProductIndexManagerTest {

    private static final String ALIAS_WITH_V1 = "{\"products-v1\":{\"aliases\":{\"products\":{}}}}";
    private static final String ACKNOWLEDGED = "{\"acknowledged\":true}";

    private FakeOpenSearch fakeOpenSearch;
    private OpenSearchClient client;
    private ProductIndexManager indexManager;

    @BeforeEach
    void setUp() throws Exception {
        fakeOpenSearch = new FakeOpenSearch();
        var properties = OpenSearchTestSupport.properties(fakeOpenSearch.url(), "products");
        client = OpenSearchTestSupport.client(properties);
        indexManager = new ProductIndexManager(client, properties);
    }

    @AfterEach
    void tearDown() {
        OpenSearchTestSupport.close(client);
        fakeOpenSearch.close();
    }

    @Test
    void createsTheFirstVersionedIndexBehindTheAliasWhenMissing() {
        fakeOpenSearch.respond(request -> request.method().equals("HEAD")
                ? new Response(404, "")
                : new Response(200, "{\"acknowledged\":true,\"shards_acknowledged\":true,\"index\":\"products-v1\"}"));

        indexManager.requireIndex();
        indexManager.requireIndex(); // already known to exist: no further calls

        assertThat(fakeOpenSearch.requests()).extracting(FakeOpenSearch.Request::method)
                .containsExactly("HEAD", "HEAD", "PUT");
        FakeOpenSearch.Request create = fakeOpenSearch.requests().get(2);
        assertThat(create.uri()).startsWith("/products-v1");
        assertThat(create.body()).contains("\"aliases\":{\"products\":{}}", "\"dynamic\":\"strict\"",
                "\"asciifolding\"", "\"scaled_float\"", "\"colors\"");
    }

    @Test
    void anExistingAliasNeedsNoCreation() {
        fakeOpenSearch.respond(request -> new Response(200, ""));

        indexManager.requireIndex();

        assertThat(fakeOpenSearch.requests()).singleElement()
                .satisfies(request -> assertThat(request.uri()).startsWith("/_alias/products"));
    }

    @Test
    void refusesToStartOverAPlainIndexThatHasTheAliasName() {
        fakeOpenSearch.respond(request -> request.uri().startsWith("/_alias")
                ? new Response(404, "")
                : new Response(200, ""));

        assertThatThrownBy(indexManager::requireIndex)
                .isInstanceOf(SearchUnavailableException.class)
                .hasMessageContaining("plain index");
    }

    @Test
    void nextIndexGetsTheNextVersionAndIsNotBehindTheAliasYet() {
        fakeOpenSearch.respond(request -> request.method().equals("GET")
                ? new Response(200, ALIAS_WITH_V1)
                : new Response(200, "{\"acknowledged\":true,\"shards_acknowledged\":true,\"index\":\"products-v2\"}"));

        String nextIndex = indexManager.createNextIndex();

        assertThat(nextIndex).isEqualTo("products-v2");
        FakeOpenSearch.Request create = fakeOpenSearch.requests().get(1);
        assertThat(create.uri()).startsWith("/products-v2");
        assertThat(create.body()).doesNotContain("\"aliases\"");
    }

    @Test
    void switchingTheAliasAddsTheNewIndexAndRemovesTheOldOneInOneRequest() {
        fakeOpenSearch.respond(request -> request.method().equals("GET")
                ? new Response(200, ALIAS_WITH_V1)
                : new Response(200, ACKNOWLEDGED));

        indexManager.switchAliasTo("products-v2");

        FakeOpenSearch.Request update = fakeOpenSearch.requests().get(1);
        assertThat(update.uri()).startsWith("/_aliases");
        assertThat(update.body())
                .contains("\"add\":{\"alias\":\"products\",\"index\":\"products-v2\"}")
                .contains("\"remove_index\":{\"index\":\"products-v1\"}");
    }
}
