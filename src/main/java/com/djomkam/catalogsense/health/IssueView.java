package com.djomkam.catalogsense.health;

import java.time.Instant;

/** A health issue as returned by the API. */
public record IssueView(Long id, String sku, String rule, Severity severity, String field, String message,
                        Instant detectedAt) {

    public static IssueView from(HealthIssue issue) {
        return new IssueView(issue.getId(), issue.getSku(), issue.getRule(), issue.getSeverity(),
                issue.getField(), issue.getMessage(), issue.getDetectedAt());
    }
}
