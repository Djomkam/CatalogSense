package com.djomkam.catalogsense.api;

import com.djomkam.catalogsense.domain.HealthIssue;

import java.time.Instant;

public record IssueView(Long id, String sku, String rule, String severity, String field, String message, Instant detectedAt) {

    public static IssueView from(HealthIssue i) {
        return new IssueView(i.getId(), i.getSku(), i.getRule(), i.getSeverity().name(),
                i.getField(), i.getMessage(), i.getDetectedAt());
    }
}
