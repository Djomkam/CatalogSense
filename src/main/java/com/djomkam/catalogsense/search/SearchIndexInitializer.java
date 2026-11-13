package com.djomkam.catalogsense.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates the index when the app starts. If OpenSearch is down, the app still starts (ingestion
 * keeps working) and the consumer's retries will surface the problem.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalogsense.search.enabled", havingValue = "true", matchIfMissing = true)
public class SearchIndexInitializer {

    private final ProductIndexer indexer;

    @EventListener(ApplicationReadyEvent.class)
    public void createIndex() {
        try {
            indexer.ensureIndex();
        } catch (RuntimeException e) {
            log.warn("Could not create the search index at startup: {}", e.toString());
        }
    }
}
