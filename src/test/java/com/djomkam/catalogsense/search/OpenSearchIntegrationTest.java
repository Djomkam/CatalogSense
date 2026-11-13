package com.djomkam.catalogsense.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Mapping, versioning and relevance against a real OpenSearch. Skipped when Docker is not available. */
@Testcontainers(disabledWithoutDocker = true)
class OpenSearchIntegrationTest {

    @Container
    static OpensearchContainer<?> opensearch =
            new OpensearchContainer<>(DockerImageName.parse("opensearchproject/opensearch:2.18.0"));

    static OpenSearchClient client;
    static ProductIndexer indexer;
    static SearchService search;

    @BeforeAll
    static void setUp() throws Exception {
        var properties = new SearchProperties(true, opensearch.getHttpHostAddress(), "it-products",
                new SearchProperties.Consumer("g", 0, Duration.ZERO));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        client = new OpenSearchConfig().openSearchClient(properties, mapper);
        indexer = new ProductIndexer(client, properties);
        search = new SearchService(client, properties);

        indexer.ensureIndex();
        indexer.upsert(doc("JKT-1", "Trail Jacket", "Northpeak", "Jackets", "189.00", true, List.of("Red", "Black"), 1));
        indexer.upsert(doc("JKT-2", "Rain Jacket", "Acme", "Jackets", "99.00", false, List.of("Blue"), 1));
        indexer.upsert(doc("TEE-1", "Écharpe en laine", "Acme", "Accessories", "35.00", true, List.of("Red"), 1));
        indexer.upsert(doc("OTHER", "Trail Jacket", "Northpeak", "Jackets", "189.00", true, List.of("Red"), 1)
                .withCatalog("other-store"));
        client.indices().refresh(r -> r.index("it-products"));
    }

    private static ProductDocument doc(String sku, String name, String brand, String category, String price,
                                       boolean inStock, List<String> colors, long version) {
        return new ProductDocument("it-store", sku, name, brand, category, new BigDecimal(price), "CAD", null,
                inStock, version, Instant.parse("2026-11-13T00:00:00Z"), colors, List.of("M"));
    }

    private static ProductSearchCriteria text(String q) {
        return new ProductSearchCriteria("it-store", q, null, null, null, null, null, null, null, null, 0, 20);
    }

    @Test
    void typoToleranceAndAccentFolding() {
        assertThat(search.search(text("jakcet")).hits()).extracting(SearchResult.Hit::sku)
                .containsExactlyInAnyOrder("JKT-1", "JKT-2");
        assertThat(search.search(text("echarpe")).hits()).extracting(SearchResult.Hit::sku)
                .containsExactly("TEE-1");
    }

    @Test
    void inStockProductsRankFirstAndOtherCatalogsAreHidden() {
        SearchResult result = search.search(text("jacket"));

        assertThat(result.hits()).extracting(SearchResult.Hit::sku).containsExactly("JKT-1", "JKT-2");
    }

    @Test
    void facetsCountAcrossTheResultAndIgnoreTheirOwnSelection() {
        var acmeOnly = new ProductSearchCriteria("it-store", null, List.of("Acme"), null, null, null, null, null,
                null, null, 0, 20);

        SearchResult result = search.search(acmeOnly);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.facets().get("brand")).containsExactlyInAnyOrder(
                new SearchResult.FacetValue("Acme", 2), new SearchResult.FacetValue("Northpeak", 1));
        assertThat(result.facets().get("color")).containsExactlyInAnyOrder(
                new SearchResult.FacetValue("Blue", 1), new SearchResult.FacetValue("Red", 1));
        assertThat(result.priceRange().min()).isEqualByComparingTo("35.00");
    }

    @Test
    void olderVersionsAreRefused() {
        assertThat(indexer.upsert(doc("JKT-1", "Old name", "Northpeak", "Jackets", "1.00", true, List.of(), 1)))
                .isFalse();
    }
}
