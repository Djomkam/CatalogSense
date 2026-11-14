package com.djomkam.catalogsense.health;

/** How many open issues one rule raised at one severity (a query projection, also returned by the API). */
public record RuleIssueCount(String rule, Severity severity, long count) {
}
