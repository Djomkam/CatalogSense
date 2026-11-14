package com.djomkam.catalogsense.health;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/** One open Data Health problem of one SKU, as shown to the merchant. Immutable once created. */
@Entity
@Table(name = "health_issue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
public class HealthIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "catalog_id", nullable = false)
    private String catalogId;

    @Column(nullable = false)
    private String sku;

    /** Id of the rule that found it, e.g. "PRICE". */
    @Column(nullable = false)
    private String rule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    /** Field the problem is about, e.g. "price" or "variants[TR-9].sku". */
    private String field;

    @Column(nullable = false)
    private String message;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    public HealthIssue(String catalogId, String sku, String rule, Severity severity,
                       String field, String message, Instant detectedAt) {
        this.catalogId = catalogId;
        this.sku = sku;
        this.rule = rule;
        this.severity = severity;
        this.field = field;
        this.message = message;
        this.detectedAt = detectedAt;
    }

    /**
     * Entity identity is the database id. Two unsaved instances (id == null) are never equal,
     * and the hash code is stable across persist, as recommended for JPA entities.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HealthIssue otherIssue)) {
            return false;
        }
        return id != null && id.equals(otherIssue.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
