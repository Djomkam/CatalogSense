package com.djomkam.catalogsense.search;

import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;
import org.opensearch.client.opensearch.core.SearchRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns {@link ProductSearchCriteria} into an OpenSearch query. Pure: no client, easy to test.
 *
 * <h2>How the query is built</h2>
 * <ul>
 *   <li><b>Text:</b> multi_match over name, brand, category and colors with {@code fuzziness: AUTO}
 *       (typo tolerance: "jakcet" finds "jacket"). The first letter must match, which keeps
 *       fuzzy matches sensible and cheap.</li>
 *   <li><b>In-stock boost:</b> a {@code should} clause adds score to in-stock products, so they
 *       rank first without hiding sold-out ones.</li>
 *   <li><b>Filters that are not facets</b> (catalog, in stock, price range) go in the bool
 *       {@code filter}: they don't affect the score and OpenSearch can cache them.</li>
 *   <li><b>Facet selections</b> go in {@code post_filter}, and each facet's aggregation applies
 *       every <i>other</i> facet's selection. Picking brand "Acme" therefore narrows the hits and
 *       the color counts, but the brand list still shows the other brands with their counts, so
 *       the shopper can add a second brand. This is called disjunctive faceting.</li>
 * </ul>
 */
public final class SearchRequestFactory {

    public static final String BRAND = "brand";
    public static final String CATEGORY = "category";
    public static final String COLOR = "color";
    public static final String SIZE = "size";
    public static final String PRICE = "price";

    static final int FACET_SIZE = 20;
    static final double IN_STOCK_BOOST = 2.0;

    private static final Map<String, String> FACET_FIELDS = Map.of(
            BRAND, "brand", CATEGORY, "category", COLOR, "colors", SIZE, "sizes");

    private SearchRequestFactory() {
    }

    public static SearchRequest build(String index, ProductSearchCriteria c) {
        Map<String, Query> facetFilters = facetFilters(c);

        Query main = Query.of(q -> q.bool(b -> {
            b.filter(term("catalogId", c.catalogId()));
            if (c.inStock() != null) {
                b.filter(Query.of(f -> f.term(t -> t.field("inStock").value(FieldValue.of(c.inStock())))));
            }
            if (c.minPrice() != null || c.maxPrice() != null) {
                b.filter(Query.of(f -> f.range(r -> {
                    r.field("price");
                    if (c.minPrice() != null) {
                        r.gte(JsonData.of(c.minPrice()));
                    }
                    if (c.maxPrice() != null) {
                        r.lte(JsonData.of(c.maxPrice()));
                    }
                    return r;
                })));
            }
            if (c.hasText()) {
                b.must(Query.of(m -> m.multiMatch(mm -> mm
                        .query(c.text().trim())
                        .fields("name^3", "brand.text^2", "category.text", "colors.text")
                        .type(TextQueryType.BestFields)
                        .operator(Operator.And)
                        .fuzziness("AUTO")
                        .prefixLength(1))));
            } else {
                b.must(Query.of(m -> m.matchAll(a -> a)));
            }
            b.should(Query.of(s -> s.term(t -> t.field("inStock").value(FieldValue.of(true)).boost((float) IN_STOCK_BOOST))));
            return b;
        }));

        Map<String, Aggregation> aggs = new LinkedHashMap<>();
        FACET_FIELDS.forEach((facet, field) -> aggs.put(facet, Aggregation.of(a -> a
                .filter(allExcept(facetFilters, facet))
                .aggregations("values", Aggregation.of(t -> t.terms(tt -> tt.field(field).size(FACET_SIZE)))))));
        aggs.put(PRICE, Aggregation.of(a -> a
                .filter(allExcept(facetFilters, null))
                .aggregations("stats", Aggregation.of(s -> s.stats(st -> st.field("price"))))));

        return SearchRequest.of(r -> {
            r.index(index)
                    .query(main)
                    .aggregations(aggs)
                    .from(c.page() * c.pageSize())
                    .size(c.pageSize())
                    .trackTotalHits(t -> t.enabled(true))
                    .sort(sort(c.sort()));
            if (!facetFilters.isEmpty()) {
                r.postFilter(allExcept(facetFilters, null));
            }
            return r;
        });
    }

    private static Map<String, Query> facetFilters(ProductSearchCriteria c) {
        Map<String, Query> filters = new LinkedHashMap<>();
        addTerms(filters, BRAND, c.brands());
        addTerms(filters, CATEGORY, c.categories());
        addTerms(filters, COLOR, c.colors());
        addTerms(filters, SIZE, c.sizes());
        return filters;
    }

    private static void addTerms(Map<String, Query> filters, String facet, List<String> values) {
        if (!values.isEmpty()) {
            List<FieldValue> fieldValues = values.stream().map(FieldValue::of).toList();
            filters.put(facet, Query.of(q -> q.terms(t -> t.field(FACET_FIELDS.get(facet)).terms(v -> v.value(fieldValues)))));
        }
    }

    /** AND of every facet filter except {@code excluded} (null = all of them). */
    private static Query allExcept(Map<String, Query> facetFilters, String excluded) {
        List<Query> others = facetFilters.entrySet().stream()
                .filter(e -> !e.getKey().equals(excluded))
                .map(Map.Entry::getValue)
                .toList();
        return others.isEmpty()
                ? Query.of(q -> q.matchAll(a -> a))
                : Query.of(q -> q.bool(b -> b.filter(others)));
    }

    private static List<SortOptions> sort(ProductSearchCriteria.SortOrder order) {
        return switch (order) {
            case RELEVANCE -> List.of(score(), field("sku", true));
            case PRICE_ASC -> List.of(field("price", true), field("sku", true));
            case PRICE_DESC -> List.of(field("price", false), field("sku", true));
            case NEWEST -> List.of(field("updatedAt", false), field("sku", true));
        };
    }

    private static SortOptions score() {
        return SortOptions.of(s -> s.score(sc -> sc.order(org.opensearch.client.opensearch._types.SortOrder.Desc)));
    }

    private static SortOptions field(String name, boolean ascending) {
        return SortOptions.of(s -> s.field(f -> f.field(name).order(ascending
                ? org.opensearch.client.opensearch._types.SortOrder.Asc
                : org.opensearch.client.opensearch._types.SortOrder.Desc)));
    }

    private static Query term(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(FieldValue.of(value))));
    }
}
