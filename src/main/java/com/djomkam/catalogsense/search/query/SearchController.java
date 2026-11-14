package com.djomkam.catalogsense.search.query;

import com.djomkam.catalogsense.catalog.CatalogId;
import com.djomkam.catalogsense.search.ConditionalOnSearchEnabled;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Shopper search. If OpenSearch is unreachable, the service throws {@code SearchUnavailableException},
 * which {@code ApiErrorHandler} turns into a 503 (ingestion is not affected).
 */
@RestController
@RequestMapping("/api/catalogs/{catalogId}/search")
@Validated
@RequiredArgsConstructor
@ConditionalOnSearchEnabled
public class SearchController {

    private final SearchService searchService;

    /**
     * Example: {@code GET /api/catalogs/demo/search?q=jacket&brand=Acme&color=Red&inStock=true&sort=PRICE_ASC}.
     * Repeat a facet parameter to select several values ({@code brand=Acme&brand=Zest}).
     * Pages stop at 10,000 results (OpenSearch's default max_result_window); deeper paging
     * would use search_after.
     */
    @GetMapping
    public SearchResult search(@PathVariable @CatalogId String catalogId,
                               @RequestParam(name = "q", required = false) String text,
                               @RequestParam(name = "brand", required = false) List<String> brands,
                               @RequestParam(name = "category", required = false) List<String> categories,
                               @RequestParam(name = "color", required = false) List<String> colors,
                               @RequestParam(name = "size", required = false) List<String> sizes,
                               @RequestParam(required = false) Boolean inStock,
                               @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
                               @RequestParam(required = false) @PositiveOrZero BigDecimal maxPrice,
                               @RequestParam(name = "sort", defaultValue = "RELEVANCE") SortBy sortBy,
                               @RequestParam(defaultValue = "0") @Min(0) @Max(99) int page,
                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return searchService.search(ProductSearchCriteria.builder()
                .catalogId(catalogId)
                .text(text)
                .brands(brands)
                .categories(categories)
                .colors(colors)
                .sizes(sizes)
                .inStock(inStock)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .sortBy(sortBy)
                .page(page)
                .pageSize(pageSize)
                .build());
    }
}
