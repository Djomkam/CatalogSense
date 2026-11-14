package com.djomkam.catalogsense.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings for the OpenSearch side ({@code catalogsense.search.*}).
 *
 * @param enabled    turns the whole search feature (client, indexer, consumer, endpoints) on or off
 * @param url        OpenSearch base URL
 * @param indexAlias name the app reads and writes through; it points at one physical index
 *                   ({@code <indexAlias>-v1}, {@code -v2}, ...) so a reindex can swap indexes atomically
 * @param consumer   Kafka consumer that keeps the index in sync with the product events
 */
@ConfigurationProperties("catalogsense.search")
public record SearchProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("http://localhost:9200") String url,
        @DefaultValue("catalogsense-products") String indexAlias,
        @DefaultValue Consumer consumer
) {

    /**
     * @param groupId      consumer group of the indexer; each group gets every event once
     * @param retries      attempts after the first failure before a message goes to the dead-letter topic
     * @param retryBackoff pause between attempts
     */
    public record Consumer(
            @DefaultValue("catalogsense-search-indexer") String groupId,
            @DefaultValue("3") int retries,
            @DefaultValue("1s") Duration retryBackoff
    ) {
    }
}
