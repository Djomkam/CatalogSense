package com.djomkam.catalogsense.search;

import com.djomkam.catalogsense.search.indexing.ProductIndexManager;
import com.djomkam.catalogsense.search.indexing.ProductIndexer;
import com.djomkam.catalogsense.search.query.ProductSearchCriteria;
import com.djomkam.catalogsense.search.query.SearchResult;
import com.djomkam.catalogsense.search.query.SearchService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Mapping, versioning, relevance and the alias switch against a real OpenSearch. Skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
class OpenSearchIntegrationTest {

    private static final String CATALOG = "it-store";
    private static final String ALIAS = "it-products";

    @Container
    static OpensearchContainer<?> opensearch =
            new OpensearchContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.18.0"));

    static OpenSearchClient client;
    static ProductIndexer indexer;
    static SearchService searchService;

    @BeforeAll
    static void indexSampleProducts() throws IOException {
        var properties = OpenSearchTestSupport.properties(opensearch.getHttpHostAddress(), ALIAS);
        client = OpenSearchTestSupport.client(properties);
        indexer = new ProductIndexer(client, new ProductIndexManager(client, properties));
        searchService = new SearchService(client, properties);

        indexer.upsert(product(CATALOG, "JKT-1", "Trail Jacket", "Northpeak", "Jackets", "189.00", true, List.of("Red", "Black"), 1));
        indexer.upsert(product(CATALOG, "JKT-2", "Rain Jacket", "Acme", "Jackets", "99.00", false, List.of("Blue"), 1));
        indexer.upsert(product(CATALOG, "TEE-1", "Écharpe en laine", "Acme", "Accessories", "35.00", true, List.of("Red"), 1));
        indexer.upsert(product("other-store", "OTHER", "Trail Jacket", "Northpeak", "Jackets", "189.00", true, List.of("Red"), 1));
        client.indices().refresh(request -> request.index(ALIAS));
    }

    @AfterAll
    static void closeClient() {
        OpenSearchTestSupport.close(client);
    }

    private static ProductDocument product(String catalogId, String sku, String name, String brand, String category,
                                           String price, boolean inStock, List<String> colors, long version) {
        return new ProductDocument(catalogId, sku, name, brand, category, new BigDecimal(price), "CAD", null,
                inStock, version, OpenSearchTestSupport.UPDATED_AT, colors, List.of("M"));
    }

    private static ProductSearchCriteria.ProductSearchCriteriaBuilder inCatalog() {
        return ProductSearchCriteria.builder().catalogId(CATALOG);
    }

    @Test
    void theAliasPointsAtTheFirstVersionedIndex() throws IOException {
        assertThat(client.indices().getAlias(request -> request.name(ALIAS)).result()).containsOnlyKeys(ALIAS + "-v1");
    }

    @Test
    void typoToleranceAndAccentFolding() {
        assertThat(searchService.search(inCatalog().text("jakcet").build()).hits()).extracting(SearchResult.Hit::sku)
                .containsExactlyInAnyOrder("JKT-1", "JKT-2");
        assertThat(searchService.search(inCatalog().text("echarpe").build()).hits()).extracting(SearchResult.Hit::sku)
                .containsExactly("TEE-1");
    }

    @Test
    void inStockProductsRankFirstAndOtherCatalogsAreHidden() {
        SearchResult result = searchService.search(inCatalog().text("jacket").build());

        assertThat(result.hits()).extracting(SearchResult.Hit::sku).containsExactly("JKT-1", "JKT-2");
    }

    @Test
    void facetsCountAcrossTheResultAndIgnoreTheirOwnSelection() {
        SearchResult result = searchService.search(inCatalog().brands(List.of("Acme")).build());

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.facets().get("brand")).containsExactlyInAnyOrder(
                new SearchResult.FacetValue("Acme", 2), new SearchResult.FacetValue("Northpeak", 1));
        assertThat(result.facets().get("color")).containsExactlyInAnyOrder(
                new SearchResult.FacetValue("Blue", 1), new SearchResult.FacetValue("Red", 1));
        assertThat(result.priceRange().min()).isEqualByComparingTo("35.00");
    }

    @Test
    void olderVersionsAreSkipped() throws IOException {
        indexer.upsert(product(CATALOG, "JKT-1", "Old name", "Northpeak", "Jackets", "1.00", true, List.of(), 1));
        client.indices().refresh(request -> request.index(ALIAS));

        assertThat(searchService.search(inCatalog().text("trail").build()).hits())
                .extracting(SearchResult.Hit::name).containsExactly("Trail Jacket");
    }

    /** Uses its own alias so the other tests keep their data. */
    @Test
    void switchingTheAliasServesTheRebuiltIndexAndDropsTheOldOne() throws IOException {
        var properties = OpenSearchTestSupport.properties(opensearch.getHttpHostAddress(), "it-swap");
        var indexManager = new ProductIndexManager(client, properties);
        var swapIndexer = new ProductIndexer(client, indexManager);
        var swapSearch = new SearchService(client, properties);
        swapIndexer.upsert(product(CATALOG, "KEPT", "Trail Jacket", "Acme", "Jackets", "10.00", true, List.of(), 1));
        swapIndexer.upsert(product(CATALOG, "ORPHAN", "Trail Jacket", "Acme", "Jackets", "10.00", true, List.of(), 1));

        String rebuiltIndex = indexManager.createNextIndex();
        assertThat(swapIndexer.bulkIndex(rebuiltIndex,
                List.of(product(CATALOG, "KEPT", "Trail Jacket", "Acme", "Jackets", "10.00", true, List.of(), 1)))).isZero();
        indexManager.switchAliasTo(rebuiltIndex);
        client.indices().refresh(request -> request.index("it-swap"));

        assertThat(rebuiltIndex).isEqualTo("it-swap-v2");
        assertThat(swapSearch.search(inCatalog().text("trail").build()).hits())
                .extracting(SearchResult.Hit::sku).containsExactly("KEPT");
        assertThat(client.indices().exists(request -> request.index("it-swap-v1")).value()).isFalse();
    }
}
