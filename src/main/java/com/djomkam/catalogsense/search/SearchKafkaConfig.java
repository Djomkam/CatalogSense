package com.djomkam.catalogsense.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Error handling for the search indexer: retry a few times (OpenSearch briefly down), then park
 * the message on {@code <topic>.DLT} with the exception in its headers and move on.
 * Messages that can never succeed (unreadable JSON) skip the retries.
 */
@Configuration
@Slf4j
@ConditionalOnProperty(name = "catalogsense.search.enabled", havingValue = "true", matchIfMissing = true)
public class SearchKafkaConfig {

    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    public NewTopic productEventsDeadLetterTopic(@Value("${catalogsense.outbox.topic}") String topic,
                                                 @Value("${catalogsense.outbox.partitions:3}") int partitions) {
        // Same partition count as the source topic: the recoverer keeps the original partition.
        return TopicBuilder.name(topic + DLT_SUFFIX).partitions(partitions).replicas(1).build();
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                                SearchProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        var backOff = new FixedBackOff(properties.consumer().retryBackoff().toMillis(), properties.consumer().retries());
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        handler.setRetryListeners((record, ex, attempt) ->
                log.warn("Search indexer failed on {} (attempt {}): {}", record.key(), attempt, ex.toString()));
        return handler;
    }
}
