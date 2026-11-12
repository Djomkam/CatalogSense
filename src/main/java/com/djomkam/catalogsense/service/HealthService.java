package com.djomkam.catalogsense.service;

import com.djomkam.catalogsense.api.HealthSummary;
import com.djomkam.catalogsense.domain.HealthIssueRepository;
import com.djomkam.catalogsense.domain.ProductRepository;
import com.djomkam.catalogsense.domain.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final ProductRepository productRepository;
    private final HealthIssueRepository healthIssueRepository;

    /**
     * Tracked SKUs are the ones stored in the catalog plus the ones rejected at ingestion.
     * The score is the share of tracked SKUs with no open issue.
     */
    @Transactional(readOnly = true)
    public HealthSummary summary(String catalogId) {
        Set<String> tracked = new HashSet<>(productRepository.findSkusByCatalogId(catalogId));
        List<String> withIssues = healthIssueRepository.findDistinctSkusWithIssues(catalogId);
        tracked.addAll(withIssues);

        long withErrors = healthIssueRepository.findDistinctSkusWithSeverity(catalogId, Severity.ERROR).size();
        long withWarnings = healthIssueRepository.findDistinctSkusWithSeverity(catalogId, Severity.WARNING).size();
        int score = tracked.isEmpty() ? 100
                : (int) Math.round(100.0 * (tracked.size() - withIssues.size()) / tracked.size());

        List<HealthSummary.RuleCount> byRule = healthIssueRepository.countByRule(catalogId).stream()
                .map(row -> new HealthSummary.RuleCount((String) row[0], ((Severity) row[1]).name(), (Long) row[2]))
                .toList();

        return new HealthSummary(catalogId, productRepository.countByCatalogId(catalogId), tracked.size(),
                withErrors, withWarnings, score, byRule);
    }
}
