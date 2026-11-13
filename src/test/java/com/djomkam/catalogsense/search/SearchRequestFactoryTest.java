package com.djomkam.catalogsense.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks the generated OpenSearch JSON, which is what a reviewer would read in a slow-query log. */
class SearchRequestFactoryTest {

    private final ObjectMapper json = new ObjectMapper();

    private JsonNode build(ProductSearchCriteria c) throws Exception {
        return json.readTree(SearchRequestFactory.build("products", c).toJsonString());
    }

    private static ProductSearchCriteria criteria(String text, List<String> brands, List<String> colors) {
        return new ProductSearchCriteria("demo", text, brands, List.of(), colors, List.of(), null, null, null,
                null, 0, 20);
    }

    @Test
    void textSearchIsFuzzyAndScopedToTheCatalog() throws Exception {
        JsonNode body = build(criteria("jakcet", List.of(), List.of()));

        JsonNode bool = body.at("/query/bool");
        assertThat(bool.at("/filter/0/term/catalogId/value").asText()).isEqualTo("demo");
        JsonNode multiMatch = bool.at("/must/0/multi_match");
        assertThat(multiMatch.get("query").asText()).isEqualTo("jakcet");
        assertThat(multiMatch.get("fuzziness").asText()).isEqualTo("AUTO");
        assertThat(multiMatch.get("operator").asText()).isEqualToIgnoringCase("and");
        assertThat(multiMatch.get("fields").toString()).contains("name^3", "brand.text^2");
    }

    @Test
    void inStockProductsAreBoostedNotFiltered() throws Exception {
        JsonNode bool = build(criteria(null, List.of(), List.of())).at("/query/bool");

        assertThat(bool.at("/must/0").has("match_all")).isTrue();
        assertThat(bool.at("/should/0/term/inStock/value").asBoolean()).isTrue();
        assertThat(bool.at("/should/0/term/inStock/boost").asDouble()).isEqualTo(SearchRequestFactory.IN_STOCK_BOOST);
        assertThat(bool.get("filter")).hasSize(1); // only the catalog
    }

    @Test
    void priceAndStockFiltersGoInTheFilterContext() throws Exception {
        var c = new ProductSearchCriteria("demo", null, null, null, null, null, true,
                new BigDecimal("10"), new BigDecimal("50"), null, 0, 20);
        JsonNode filters = build(c).at("/query/bool/filter");

        assertThat(filters).hasSize(3);
        assertThat(filters.get(1).at("/term/inStock/value").asBoolean()).isTrue();
        assertThat(filters.get(2).at("/range/price/gte").asInt()).isEqualTo(10);
        assertThat(filters.get(2).at("/range/price/lte").asInt()).isEqualTo(50);
    }

    @Test
    void facetSelectionsUsePostFilterAndEachFacetIgnoresItsOwnSelection() throws Exception {
        JsonNode body = build(criteria(null, List.of("Acme", "Zest"), List.of("Red")));

        // Hits are narrowed by both selections.
        assertThat(body.at("/post_filter/bool/filter")).hasSize(2);

        // The brand list ignores the brand selection (so other brands stay visible) but applies the color one.
        JsonNode brandFilter = body.at("/aggregations/brand/filter/bool/filter");
        assertThat(brandFilter).hasSize(1);
        assertThat(brandFilter.at("/0/terms/colors").toString()).contains("Red");

        // And the reverse for colors.
        JsonNode colorFilter = body.at("/aggregations/color/filter/bool/filter");
        assertThat(colorFilter).hasSize(1);
        assertThat(colorFilter.at("/0/terms/brand").toString()).contains("Acme", "Zest");

        // Facets that are not selected see both selections.
        assertThat(body.at("/aggregations/category/filter/bool/filter")).hasSize(2);
        assertThat(body.at("/aggregations/category/aggregations/values/terms/field").asText()).isEqualTo("category");
    }

    @Test
    void noFacetSelectedMeansNoPostFilter() throws Exception {
        JsonNode body = build(criteria("shoe", List.of(), List.of()));

        assertThat(body.has("post_filter")).isFalse();
        assertThat(body.at("/aggregations/brand/filter").has("match_all")).isTrue();
        assertThat(body.at("/aggregations/price/aggregations/stats/stats/field").asText()).isEqualTo("price");
    }

    @Test
    void pagingAndSorting() throws Exception {
        var c = new ProductSearchCriteria("demo", null, null, null, null, null, null, null, null,
                ProductSearchCriteria.SortOrder.PRICE_ASC, 2, 25);
        JsonNode body = build(c);

        assertThat(body.get("from").asInt()).isEqualTo(50);
        assertThat(body.get("size").asInt()).isEqualTo(25);
        assertThat(body.at("/sort/0/price/order").asText()).isEqualTo("asc");
        assertThat(body.at("/sort/1/sku/order").asText()).isEqualTo("asc"); // stable tie-break
    }
}
