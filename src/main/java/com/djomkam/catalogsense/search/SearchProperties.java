package com.djomkam.catalogsense.search;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Settings for the OpenSearch read side.
 *
 * @param enabled  turns the whole search feature (client, indexer, consumer, endpoint) on or off
 * @param url      OpenSearch base URL
 * @param index    name of the products index
 * @param consumer Kafka consumer that keeps the index in sync with the product events
 */
@ConfigurationProperties("catalogsense.search")
public record SearchProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("http://localhost:9200") String url,
        @DefaultValue("catalogsense-products") String index,
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
