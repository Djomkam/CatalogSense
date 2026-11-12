package com.djomkam.catalogsense.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "health_issue")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class HealthIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "catalog_id", nullable = false)
    private String catalogId;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String rule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

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
}
