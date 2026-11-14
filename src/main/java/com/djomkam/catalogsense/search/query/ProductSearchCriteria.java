package com.djomkam.catalogsense.search.query;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * A shopper's search: free text plus facet selections and filters. Build it with
 * {@code ProductSearchCriteria.builder().catalogId("demo").text("jacket").brands(List.of("Acme")).build()};
 * anything left out is empty or has its default (relevance order, first page of 20).
 *
 * <p>Values inside one facet are OR-ed (brand = Acme OR Zest); different facets are AND-ed.</p>
 *
 * @param text     free text; null or blank lists everything
 * @param inStock  true = only in stock, false = only sold out, null = both
 * @param page     zero-based page number
 */
@Builder
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
        SortBy sortBy,
        int page,
        int pageSize
) {

    public static final int DEFAULT_PAGE_SIZE = 20;

    public ProductSearchCriteria {
        brands = brands == null ? List.of() : List.copyOf(brands);
        categories = categories == null ? List.of() : List.copyOf(categories);
        colors = colors == null ? List.of() : List.copyOf(colors);
        sizes = sizes == null ? List.of() : List.copyOf(sizes);
        sortBy = sortBy == null ? SortBy.RELEVANCE : sortBy;
    }

    /** Defaults for the fields a builder call leaves out (Lombok generates the rest of this class). */
    public static class ProductSearchCriteriaBuilder {
        private SortBy sortBy = SortBy.RELEVANCE;
        private int pageSize = DEFAULT_PAGE_SIZE;
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }

    /** The values the shopper picked for one facet (empty when none). */
    public List<String> selectedValues(Facet facet) {
        return switch (facet) {
            case BRAND -> brands;
            case CATEGORY -> categories;
            case COLOR -> colors;
            case SIZE -> sizes;
        };
    }
}
