package com.djomkam.catalogsense.search;

import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.StatsAggregate;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalogsense.search.enabled", havingValue = "true", matchIfMissing = true)
public class SearchService {

    private final OpenSearchClient client;
    private final SearchProperties properties;

    public SearchResult search(ProductSearchCriteria criteria) {
        try {
            SearchResponse<ProductDocument> response =
                    client.search(SearchRequestFactory.build(properties.index(), criteria), ProductDocument.class);
            return toResult(criteria, response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static SearchResult toResult(ProductSearchCriteria criteria, SearchResponse<ProductDocument> response) {
        List<SearchResult.Hit> hits = response.hits().hits().stream()
                .filter(h -> h.source() != null)
                .map(h -> SearchResult.Hit.from(h.source(), h.score()))
                .toList();
        long total = response.hits().total() == null ? hits.size() : response.hits().total().value();

        Map<String, List<SearchResult.FacetValue>> facets = new LinkedHashMap<>();
        SearchResult.PriceRange priceRange = null;
        for (Map.Entry<String, Aggregate> entry : response.aggregations().entrySet()) {
            Map<String, Aggregate> inner = entry.getValue().filter().aggregations();
            if (SearchRequestFactory.PRICE.equals(entry.getKey())) {
                priceRange = priceRange(inner.get("stats").stats());
            } else {
                facets.put(entry.getKey(), inner.get("values").sterms().buckets().array().stream()
                        .map(b -> new SearchResult.FacetValue(b.key(), b.docCount()))
                        .toList());
            }
        }
        return new SearchResult(total, criteria.page(), criteria.pageSize(), hits, facets, priceRange);
    }

    private static SearchResult.PriceRange priceRange(StatsAggregate stats) {
        if (stats.count() == 0) {
            return null;
        }
        return new SearchResult.PriceRange(
                BigDecimal.valueOf(stats.min()).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(stats.max()).setScale(2, RoundingMode.HALF_UP));
    }
}
