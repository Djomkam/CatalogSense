package com.djomkam.catalogsense.health;

import com.djomkam.catalogsense.catalog.CatalogId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Data Health of a catalog: the score and the issues to fix. */
@RestController
@RequestMapping("/api/catalogs/{catalogId}/health")
@Validated
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping
    public HealthSummary getHealthSummary(@PathVariable @CatalogId String catalogId) {
        return healthService.summarize(catalogId);
    }

    /** Example: {@code GET /api/catalogs/demo/health/issues?rule=PRICE&severity=ERROR}. Both filters are optional. */
    @GetMapping("/issues")
    public Page<IssueView> listHealthIssues(@PathVariable @CatalogId String catalogId,
                                            @RequestParam(required = false) String rule,
                                            @RequestParam(required = false) Severity severity,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(500) int pageSize) {
        return healthService.findIssues(catalogId, rule, severity, PageRequest.of(page, pageSize));
    }
}
