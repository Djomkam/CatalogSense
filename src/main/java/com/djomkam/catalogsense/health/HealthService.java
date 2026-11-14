package com.djomkam.catalogsense.health;

import com.djomkam.catalogsense.catalog.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HealthService {

    private final ProductRepository productRepository;
    private final HealthIssueRepository healthIssueRepository;

    /**
     * Tracked SKUs are the ones stored in the catalog plus the ones rejected at ingestion (they have
     * issues but no product row). The score is the share of tracked SKUs with no open issue.
     */
    public HealthSummary summarize(String catalogId) {
        List<String> skusWithIssues = healthIssueRepository.findDistinctSkusWithIssues(catalogId);
        Set<String> trackedSkus = new HashSet<>(productRepository.findSkusByCatalogId(catalogId));
        trackedSkus.addAll(skusWithIssues);

        return new HealthSummary(
                catalogId,
                productRepository.countByCatalogId(catalogId),
                trackedSkus.size(),
                healthIssueRepository.countDistinctSkusWithSeverity(catalogId, Severity.ERROR),
                healthIssueRepository.countDistinctSkusWithSeverity(catalogId, Severity.WARNING),
                HealthScore.of(trackedSkus.size(), skusWithIssues.size()).value(),
                healthIssueRepository.countIssuesByRule(catalogId));
    }

    /** Newest first; a null or blank {@code rule} and a null {@code severity} mean "any". */
    public Page<IssueView> findIssues(String catalogId, String rule, Severity severity, Pageable pageable) {
        String ruleFilter = rule == null || rule.isBlank() ? null : rule;
        return healthIssueRepository.findIssues(catalogId, ruleFilter, severity, pageable).map(IssueView::from);
    }
}
