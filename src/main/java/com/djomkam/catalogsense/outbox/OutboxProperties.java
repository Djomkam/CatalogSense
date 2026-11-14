package com.djomkam.catalogsense.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param topic        Kafka topic for product events
 * @param partitions   partitions created for the topic (ordering is per partition, i.e. per product)
 * @param relay        relay settings
 * @param retention    how long published rows are kept before cleanup
 */
@ConfigurationProperties("catalogsense.outbox")
public record OutboxProperties(
        @DefaultValue("catalogsense.product-events") String topic,
        @DefaultValue("3") int partitions,
        @DefaultValue Relay relay,
        @DefaultValue("7d") Duration retention
) {

    /**
     * @param enabled      turn the relay off (e.g. in tests that only check the outbox table)
     * @param batchSize    rows sent per polling cycle
     * @param pollInterval delay between cycles
     * @param sendTimeout  max wait for the broker to acknowledge one message
     */
    public record Relay(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("100") int batchSize,
            @DefaultValue("500ms") Duration pollInterval,
            @DefaultValue("10s") Duration sendTimeout
    ) {
    }
}
