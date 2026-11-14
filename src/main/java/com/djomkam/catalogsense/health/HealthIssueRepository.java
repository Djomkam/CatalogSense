package com.djomkam.catalogsense.health;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface HealthIssueRepository extends JpaRepository<HealthIssue, Long> {

    @Modifying
    @Query("delete from HealthIssue i where i.catalogId = :catalogId and i.sku in :skus")
    void deleteByCatalogIdAndSkuIn(String catalogId, Collection<String> skus);

    /** Newest issues first. {@code rule} and {@code severity} are optional filters: null means any. */
    @Query(value = """
            select i from HealthIssue i
            where i.catalogId = :catalogId
              and (:rule is null or i.rule = :rule)
              and (:severity is null or i.severity = :severity)
            order by i.detectedAt desc, i.id desc""",
            countQuery = """
            select count(i) from HealthIssue i
            where i.catalogId = :catalogId
              and (:rule is null or i.rule = :rule)
              and (:severity is null or i.severity = :severity)""")
    Page<HealthIssue> findIssues(String catalogId, String rule, Severity severity, Pageable pageable);

    @Query("select distinct i.sku from HealthIssue i where i.catalogId = :catalogId")
    List<String> findDistinctSkusWithIssues(String catalogId);

    @Query("select count(distinct i.sku) from HealthIssue i where i.catalogId = :catalogId and i.severity = :severity")
    long countDistinctSkusWithSeverity(String catalogId, Severity severity);

    @Query("""
            select new com.djomkam.catalogsense.health.RuleIssueCount(i.rule, i.severity, count(i))
            from HealthIssue i
            where i.catalogId = :catalogId
            group by i.rule, i.severity
            order by count(i) desc""")
    List<RuleIssueCount> countIssuesByRule(String catalogId);
}
