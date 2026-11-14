package com.djomkam.catalogsense.search.query;

import com.djomkam.catalogsense.search.ProductFields;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Operator;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TextQueryType;
import org.opensearch.client.opensearch.core.SearchRequest;

import java.util.EnumMap;
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

    /** Inside each facet's filter aggregation: the terms aggregation with the values and counts. */
    static final String FACET_VALUES_AGGREGATION = "values";
    /** Top-level aggregation (and its inner stats aggregation) for the price slider. */
    static final String PRICE_RANGE_AGGREGATION = "price_range";
    static final String PRICE_STATS_AGGREGATION = "stats";

    static final int FACET_VALUE_LIMIT = 20;
    static final float IN_STOCK_BOOST = 2.0f;

    private static final List<String> TEXT_FIELDS = List.of(
            ProductFields.NAME + "^3", ProductFields.BRAND_TEXT + "^2", ProductFields.CATEGORY_TEXT,
            ProductFields.COLORS_TEXT);

    private SearchRequestFactory() {
    }

    public static SearchRequest build(String index, ProductSearchCriteria criteria) {
        Map<Facet, Query> facetFilters = facetFilters(criteria);

        Map<String, Aggregation> aggregations = new LinkedHashMap<>();
        for (Facet facet : Facet.values()) {
            aggregations.put(facet.apiName(), facetAggregation(facet, facetFilters));
        }
        aggregations.put(PRICE_RANGE_AGGREGATION, priceRangeAggregation(facetFilters));

        return SearchRequest.of(request -> {
            request.index(index)
                    .query(mainQuery(criteria))
                    .aggregations(aggregations)
                    .from(criteria.page() * criteria.pageSize())
                    .size(criteria.pageSize())
                    .trackTotalHits(totalHits -> totalHits.enabled(true))
                    .sort(sortOptions(criteria.sortBy()));
            if (!facetFilters.isEmpty()) {
                request.postFilter(allFiltersExcept(facetFilters, null));
            }
            return request;
        });
    }

    /** Text match, in-stock boost and the non-facet filters. Facet selections are applied later (post_filter). */
    private static Query mainQuery(ProductSearchCriteria criteria) {
        return Query.of(query -> query.bool(bool -> {
            bool.filter(termQuery(ProductFields.CATALOG_ID, FieldValue.of(criteria.catalogId())));
            if (criteria.inStock() != null) {
                bool.filter(termQuery(ProductFields.IN_STOCK, FieldValue.of(criteria.inStock())));
            }
            if (criteria.minPrice() != null || criteria.maxPrice() != null) {
                bool.filter(priceRangeQuery(criteria));
            }
            bool.must(criteria.hasText() ? textQuery(criteria.text().trim()) : matchAll());
            bool.should(Query.of(boost -> boost.term(term -> term
                    .field(ProductFields.IN_STOCK).value(FieldValue.of(true)).boost(IN_STOCK_BOOST))));
            return bool;
        }));
    }

    private static Query textQuery(String text) {
        return Query.of(query -> query.multiMatch(match -> match
                .query(text)
                .fields(TEXT_FIELDS)
                .type(TextQueryType.BestFields)
                .operator(Operator.And)
                .fuzziness("AUTO")
                .prefixLength(1)));
    }

    private static Query priceRangeQuery(ProductSearchCriteria criteria) {
        return Query.of(query -> query.range(range -> {
            range.field(ProductFields.PRICE);
            if (criteria.minPrice() != null) {
                range.gte(JsonData.of(criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                range.lte(JsonData.of(criteria.maxPrice()));
            }
            return range;
        }));
    }

    /** One terms filter per facet the shopper picked values for. */
    private static Map<Facet, Query> facetFilters(ProductSearchCriteria criteria) {
        Map<Facet, Query> filters = new EnumMap<>(Facet.class);
        for (Facet facet : Facet.values()) {
            List<String> selected = criteria.selectedValues(facet);
            if (!selected.isEmpty()) {
                List<FieldValue> values = selected.stream().map(FieldValue::of).toList();
                filters.put(facet, Query.of(query -> query.terms(terms -> terms
                        .field(facet.indexField())
                        .terms(termValues -> termValues.value(values)))));
            }
        }
        return filters;
    }

    /** Value counts for one facet, under every other facet's selection (disjunctive faceting). */
    private static Aggregation facetAggregation(Facet facet, Map<Facet, Query> facetFilters) {
        return Aggregation.of(aggregation -> aggregation
                .filter(allFiltersExcept(facetFilters, facet))
                .aggregations(FACET_VALUES_AGGREGATION, Aggregation.of(values -> values
                        .terms(terms -> terms.field(facet.indexField()).size(FACET_VALUE_LIMIT)))));
    }

    /** Min and max price of the products matching every facet selection. */
    private static Aggregation priceRangeAggregation(Map<Facet, Query> facetFilters) {
        return Aggregation.of(aggregation -> aggregation
                .filter(allFiltersExcept(facetFilters, null))
                .aggregations(PRICE_STATS_AGGREGATION, Aggregation.of(stats -> stats
                        .stats(priceStats -> priceStats.field(ProductFields.PRICE)))));
    }

    /** AND of every facet filter except {@code excludedFacet} (null keeps all of them). */
    private static Query allFiltersExcept(Map<Facet, Query> facetFilters, Facet excludedFacet) {
        List<Query> remaining = facetFilters.entrySet().stream()
                .filter(entry -> entry.getKey() != excludedFacet)
                .map(Map.Entry::getValue)
                .toList();
        return remaining.isEmpty() ? matchAll() : Query.of(query -> query.bool(bool -> bool.filter(remaining)));
    }

    private static List<SortOptions> sortOptions(SortBy sortBy) {
        SortOptions skuTieBreak = fieldSort(ProductFields.SKU, SortOrder.Asc);
        return switch (sortBy) {
            case RELEVANCE -> List.of(SortOptions.of(sort -> sort.score(score -> score.order(SortOrder.Desc))), skuTieBreak);
            case PRICE_ASC -> List.of(fieldSort(ProductFields.PRICE, SortOrder.Asc), skuTieBreak);
            case PRICE_DESC -> List.of(fieldSort(ProductFields.PRICE, SortOrder.Desc), skuTieBreak);
            case NEWEST -> List.of(fieldSort(ProductFields.UPDATED_AT, SortOrder.Desc), skuTieBreak);
        };
    }

    private static SortOptions fieldSort(String field, SortOrder order) {
        return SortOptions.of(sort -> sort.field(byField -> byField.field(field).order(order)));
    }

    private static Query termQuery(String field, FieldValue value) {
        return Query.of(query -> query.term(term -> term.field(field).value(value)));
    }

    private static Query matchAll() {
        return Query.of(query -> query.matchAll(all -> all));
    }
}
