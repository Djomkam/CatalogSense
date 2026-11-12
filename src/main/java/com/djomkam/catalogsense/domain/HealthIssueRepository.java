package com.djomkam.catalogsense.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface HealthIssueRepository extends JpaRepository<HealthIssue, Long> {

    @Modifying
    @Query("delete from HealthIssue i where i.catalogId = :catalogId and i.sku = :sku")
    void deleteByCatalogIdAndSku(String catalogId, String sku);

    @Modifying
    @Query("delete from HealthIssue i where i.catalogId = :catalogId and i.sku in :skus")
    void deleteByCatalogIdAndSkuIn(String catalogId, Collection<String> skus);

    Page<HealthIssue> findByCatalogIdOrderByDetectedAtDesc(String catalogId, Pageable pageable);

    Page<HealthIssue> findByCatalogIdAndSeverityOrderByDetectedAtDesc(String catalogId, Severity severity, Pageable pageable);

    Page<HealthIssue> findByCatalogIdAndRuleOrderByDetectedAtDesc(String catalogId, String rule, Pageable pageable);

    Page<HealthIssue> findByCatalogIdAndRuleAndSeverityOrderByDetectedAtDesc(String catalogId, String rule, Severity severity, Pageable pageable);

    @Query("select distinct i.sku from HealthIssue i where i.catalogId = :catalogId")
    List<String> findDistinctSkusWithIssues(String catalogId);

    @Query("select distinct i.sku from HealthIssue i where i.catalogId = :catalogId and i.severity = :severity")
    List<String> findDistinctSkusWithSeverity(String catalogId, Severity severity);

    @Query("select i.rule, i.severity, count(i) from HealthIssue i where i.catalogId = :catalogId group by i.rule, i.severity order by count(i) desc")
    List<Object[]> countByRule(String catalogId);
}
