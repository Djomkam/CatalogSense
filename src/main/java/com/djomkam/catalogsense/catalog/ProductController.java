package com.djomkam.catalogsense.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Stored products of a catalog, as PostgreSQL holds them (not the search index). */
@RestController
@RequestMapping("/api/catalogs/{catalogId}/products")
@Validated
@RequiredArgsConstructor
public class ProductController {

    private final ProductQueryService productQueryService;

    @GetMapping
    public Page<ProductView> listProducts(@PathVariable @CatalogId String catalogId,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(200) int pageSize) {
        return productQueryService.listProducts(catalogId, page, pageSize);
    }

    @GetMapping("/{sku}")
    public ProductView getProduct(@PathVariable @CatalogId String catalogId, @PathVariable String sku) {
        return productQueryService.findProduct(catalogId, sku)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No product " + sku));
    }
}
