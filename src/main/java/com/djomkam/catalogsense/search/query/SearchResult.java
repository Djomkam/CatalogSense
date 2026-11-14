package com.djomkam.catalogsense.search.query;

import com.djomkam.catalogsense.search.ProductDocument;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Response of the search endpoint: one page of hits plus the facet counts for the whole result.
 *
 * @param facets value counts per facet, keyed by {@link Facet#apiName()} ("brand", "color", ...)
 */
public record SearchResult(
        long total,
        int page,
        int pageSize,
        List<Hit> hits,
        Map<String, List<FacetValue>> facets,
        PriceRange priceRange
) {

    public record Hit(String sku, String name, String brand, String category, BigDecimal price, String currency,
                      String imageUrl, boolean inStock, List<String> colors, List<String> sizes, Double score) {

        static Hit from(ProductDocument document, Double score) {
            return new Hit(document.sku(), document.name(), document.brand(), document.category(), document.price(),
                    document.currency(), document.imageUrl(), document.inStock(), document.colors(), document.sizes(),
                    score);
        }
    }

    public record FacetValue(String value, long count) {
    }

    /** Min and max price of the matching products, for a price slider. Null when nothing matches. */
    public record PriceRange(BigDecimal min, BigDecimal max) {
    }
}
