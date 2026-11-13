package com.djomkam.catalogsense.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/catalogs/{catalogId}/search")
@Validated
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalogsense.search.enabled", havingValue = "true", matchIfMissing = true)
public class SearchController {

    private static final String CATALOG_ID = "[A-Za-z0-9_-]{1,64}";

    private final SearchService searchService;
    private final SearchReindexService reindexService;

    /**
     * Example: {@code GET /api/catalogs/demo/search?q=jacket&brand=Acme&color=Red&inStock=true&sort=PRICE_ASC}.
     * Repeat a facet parameter to select several values ({@code brand=Acme&brand=Zest}).
     * Pages stop at 10,000 results (OpenSearch's default max_result_window); deeper paging
     * would use search_after.
     */
    @GetMapping
    public SearchResult search(@PathVariable @Pattern(regexp = CATALOG_ID) String catalogId,
                               @RequestParam(name = "q", required = false) String text,
                               @RequestParam(name = "brand", required = false) List<String> brands,
                               @RequestParam(name = "category", required = false) List<String> categories,
                               @RequestParam(name = "color", required = false) List<String> colors,
                               @RequestParam(name = "size", required = false) List<String> sizes,
                               @RequestParam(required = false) Boolean inStock,
                               @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
                               @RequestParam(required = false) @PositiveOrZero BigDecimal maxPrice,
                               @RequestParam(defaultValue = "RELEVANCE") ProductSearchCriteria.SortOrder sort,
                               @RequestParam(defaultValue = "0") @Min(0) @Max(99) int page,
                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return searchService.search(new ProductSearchCriteria(catalogId, text, brands, categories, colors, sizes,
                inStock, minPrice, maxPrice, sort, page, pageSize));
    }

    /** Search is a secondary read model: if OpenSearch is unreachable, say so instead of a generic 500. */
    @ExceptionHandler({java.io.UncheckedIOException.class, org.opensearch.client.opensearch._types.OpenSearchException.class})
    public org.springframework.http.ProblemDetail searchUnavailable(RuntimeException e) {
        var problem = org.springframework.http.ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "Search is temporarily unavailable. Catalog ingestion is not affected.");
        problem.setTitle("Search unavailable");
        return problem;
    }

    /** Rebuilds this catalog's search documents from the database. */
    @PostMapping("/reindex")
    public SearchReindexService.ReindexReport reindex(@PathVariable @Pattern(regexp = CATALOG_ID) String catalogId) {
        return reindexService.reindex(catalogId);
    }
}
