package com.djomkam.catalogsense.web;

import com.djomkam.catalogsense.api.*;
import com.djomkam.catalogsense.domain.HealthIssueRepository;
import com.djomkam.catalogsense.domain.Severity;
import com.djomkam.catalogsense.service.HealthService;
import com.djomkam.catalogsense.service.IngestionMode;
import com.djomkam.catalogsense.service.IngestionService;
import com.djomkam.catalogsense.service.ProductQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/catalogs/{catalogId}")
@Validated
@RequiredArgsConstructor
public class CatalogController {

    static final int MAX_BATCH = 10_000;
    private static final String CATALOG_ID = "[A-Za-z0-9_-]{1,64}";

    private final IngestionService ingestionService;
    private final HealthService healthService;
    private final ProductQueryService productQueryService;
    private final HealthIssueRepository healthIssueRepository;

    /** Full load: the body is the whole catalog. Products not in it are deleted. */
    @PutMapping("/products")
    public IngestionReport fullLoad(@PathVariable @Pattern(regexp = CATALOG_ID) String catalogId,
                                    @RequestBody List<ProductPayload> body) {
        checkSize(body);
        return ingestionService.ingest(catalogId, body, IngestionMode.FULL);
    }

    /** Partial update: only the products in the body are created or updated. */
    @PatchMapping("/products")
    public IngestionReport partialUpdate(@PathVariable @Pattern(regexp = CATALOG_ID) String catalogId,
                                         @RequestBody List<ProductPayload> body) {
        checkSize(body);
        return ingestionService.ingest(catalogId, body, IngestionMode.PARTIAL);
    }

    @GetMapping("/products")
    public Page<ProductView> list(@PathVariable @Pattern(regexp = CATALOG_ID) String catalogId,
                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                  @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return productQueryService.list(catalogId, page, size);
    }

    @GetMapping("/products/{sku}")
    public ProductView get(@PathVariable @Pattern(regexp = CATALOG_ID) String catalogId, @PathVariable String sku) {
        return productQueryService.get(catalogId, sku)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No product " + sku));
    }

    @GetMapping("/health")
    public HealthSummary health(@PathVariable @Pattern(regexp = CATALOG_ID) String catalogId) {
        return healthService.summary(catalogId);
    }

    @GetMapping("/health/issues")
    public Page<IssueView> issues(@PathVariable @Pattern(regexp = CATALOG_ID) String catalogId,
                                  @RequestParam(required = false) String rule,
                                  @RequestParam(required = false) Severity severity,
                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                  @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        PageRequest pr = PageRequest.of(page, size);
        Page<com.djomkam.catalogsense.domain.HealthIssue> result;
        if (rule != null && !rule.isBlank() && severity != null) {
            result = healthIssueRepository.findByCatalogIdAndRuleAndSeverityOrderByDetectedAtDesc(catalogId, rule, severity, pr);
        } else if (rule != null && !rule.isBlank()) {
            result = healthIssueRepository.findByCatalogIdAndRuleOrderByDetectedAtDesc(catalogId, rule, pr);
        } else if (severity != null) {
            result = healthIssueRepository.findByCatalogIdAndSeverityOrderByDetectedAtDesc(catalogId, severity, pr);
        } else {
            result = healthIssueRepository.findByCatalogIdOrderByDetectedAtDesc(catalogId, pr);
        }
        return result.map(IssueView::from);
    }

    private static void checkSize(List<ProductPayload> body) {
        if (body.size() > MAX_BATCH) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Send at most " + MAX_BATCH + " products per request (got " + body.size() + ").");
        }
    }
}
