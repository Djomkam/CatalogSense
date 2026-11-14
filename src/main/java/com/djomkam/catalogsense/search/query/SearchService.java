package com.djomkam.catalogsense.search.query;

import com.djomkam.catalogsense.search.ConditionalOnSearchEnabled;
import com.djomkam.catalogsense.search.ProductDocument;
import com.djomkam.catalogsense.search.SearchProperties;
import com.djomkam.catalogsense.search.SearchUnavailableException;
import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.StatsAggregate;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.djomkam.catalogsense.search.query.SearchRequestFactory.FACET_VALUES_AGGREGATION;
import static com.djomkam.catalogsense.search.query.SearchRequestFactory.PRICE_RANGE_AGGREGATION;
import static com.djomkam.catalogsense.search.query.SearchRequestFactory.PRICE_STATS_AGGREGATION;

@Service
@RequiredArgsConstructor
@ConditionalOnSearchEnabled
public class SearchService {

    private final OpenSearchClient client;
    private final SearchProperties properties;

    public SearchResult search(ProductSearchCriteria criteria) {
        try {
            SearchResponse<ProductDocument> response = client.search(
                    SearchRequestFactory.build(properties.indexAlias(), criteria), ProductDocument.class);
            return toSearchResult(criteria, response);
        } catch (OpenSearchException | IOException e) {
            throw new SearchUnavailableException("Search request failed", e);
        }
    }

    static SearchResult toSearchResult(ProductSearchCriteria criteria, SearchResponse<ProductDocument> response) {
        List<SearchResult.Hit> hits = response.hits().hits().stream()
                .filter(hit -> hit.source() != null)
                .map(hit -> SearchResult.Hit.from(hit.source(), hit.score()))
                .toList();
        long total = response.hits().total() == null ? hits.size() : response.hits().total().value();
        Map<String, Aggregate> aggregations = response.aggregations();

        Map<String, List<SearchResult.FacetValue>> facets = new LinkedHashMap<>();
        for (Facet facet : Facet.values()) {
            Aggregate facetAggregate = aggregations.get(facet.apiName());
            if (facetAggregate != null) {
                facets.put(facet.apiName(), facetValues(facetAggregate));
            }
        }
        Aggregate priceAggregate = aggregations.get(PRICE_RANGE_AGGREGATION);
        SearchResult.PriceRange priceRange = priceAggregate == null ? null
                : priceRange(priceAggregate.filter().aggregations().get(PRICE_STATS_AGGREGATION).stats());

        return new SearchResult(total, criteria.page(), criteria.pageSize(), hits, facets, priceRange);
    }

    private static List<SearchResult.FacetValue> facetValues(Aggregate facetAggregate) {
        return facetAggregate.filter().aggregations().get(FACET_VALUES_AGGREGATION).sterms().buckets().array().stream()
                .map(bucket -> new SearchResult.FacetValue(bucket.key(), bucket.docCount()))
                .toList();
    }

    /** Null when nothing matches, so the client can hide the price slider. */
    private static SearchResult.PriceRange priceRange(StatsAggregate priceStats) {
        if (priceStats.count() == 0) {
            return null;
        }
        return new SearchResult.PriceRange(
                BigDecimal.valueOf(priceStats.min()).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(priceStats.max()).setScale(2, RoundingMode.HALF_UP));
    }
}
