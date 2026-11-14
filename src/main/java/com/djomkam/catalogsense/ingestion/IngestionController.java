package com.djomkam.catalogsense.ingestion;

import com.djomkam.catalogsense.catalog.CatalogId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Catalog intake: full loads and partial updates. */
@RestController
@RequestMapping("/api/catalogs/{catalogId}/products")
@Validated
@RequiredArgsConstructor
public class IngestionController {

    static final int MAX_PRODUCTS_PER_REQUEST = 10_000;

    private final IngestionService ingestionService;

    /** Full load: the body is the whole catalog. Products not in it are deleted. */
    @PutMapping
    public IngestionReport fullLoad(@PathVariable @CatalogId String catalogId,
                                    @RequestBody List<ProductPayload> products) {
        rejectIfBatchTooLarge(products);
        return ingestionService.ingest(catalogId, products, IngestionMode.FULL);
    }

    /** Partial update: only the products in the body are created or updated. */
    @PatchMapping
    public IngestionReport partialUpdate(@PathVariable @CatalogId String catalogId,
                                         @RequestBody List<ProductPayload> products) {
        rejectIfBatchTooLarge(products);
        return ingestionService.ingest(catalogId, products, IngestionMode.PARTIAL);
    }

    private static void rejectIfBatchTooLarge(List<ProductPayload> products) {
        if (products.size() > MAX_PRODUCTS_PER_REQUEST) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Send at most " + MAX_PRODUCTS_PER_REQUEST + " products per request (got " + products.size() + ").");
        }
    }
}
