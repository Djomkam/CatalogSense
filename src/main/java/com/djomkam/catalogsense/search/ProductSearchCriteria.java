package com.djomkam.catalogsense.search;

import java.math.BigDecimal;
import java.util.List;

/**
 * A shopper's search: free text plus facet selections and filters.
 * Values inside one facet are OR-ed (brand = Acme OR Zest); different facets are AND-ed.
 */
public record ProductSearchCriteria(
        String catalogId,
        String text,
        List<String> brands,
        List<String> categories,
        List<String> colors,
        List<String> sizes,
        Boolean inStock,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        SortOrder sort,
        int page,
        int pageSize
) {

    public ProductSearchCriteria {
        brands = brands == null ? List.of() : List.copyOf(brands);
        categories = categories == null ? List.of() : List.copyOf(categories);
        colors = colors == null ? List.of() : List.copyOf(colors);
        sizes = sizes == null ? List.of() : List.copyOf(sizes);
        sort = sort == null ? SortOrder.RELEVANCE : sort;
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    public enum SortOrder { RELEVANCE, PRICE_ASC, PRICE_DESC, NEWEST }
}
