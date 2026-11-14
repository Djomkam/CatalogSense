package com.djomkam.catalogsense.search.indexing;

import com.djomkam.catalogsense.search.ConditionalOnSearchEnabled;
import com.djomkam.catalogsense.search.SearchProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
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
 * Messages that can never succeed (unreadable JSON, an upsert without a product) skip the retries.
 */
@Configuration
@Slf4j
@ConditionalOnSearchEnabled
public class SearchKafkaConfig {

    public static final String DEAD_LETTER_SUFFIX = ".DLT";

    @Bean
    public NewTopic productEventsDeadLetterTopic(@Value("${catalogsense.outbox.topic}") String topic,
                                                 @Value("${catalogsense.outbox.partitions:3}") int partitions) {
        // Same partition count as the source topic: the recoverer keeps the original partition.
        return TopicBuilder.name(topic + DEAD_LETTER_SUFFIX).partitions(partitions).replicas(1).build();
    }

    @Bean
    public CommonErrorHandler retryThenDeadLetterErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                                              SearchProperties properties) {
        var deadLetterPublisher = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (failedRecord, error) -> new TopicPartition(failedRecord.topic() + DEAD_LETTER_SUFFIX,
                        failedRecord.partition()));
        var retryPolicy = new FixedBackOff(properties.consumer().retryBackoff().toMillis(),
                properties.consumer().retries());
        var errorHandler = new DefaultErrorHandler(deadLetterPublisher, retryPolicy);
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        errorHandler.setRetryListeners((failedRecord, error, attempt) ->
                log.warn("Search indexer failed on {} (attempt {}): {}", failedRecord.key(), attempt, error.toString()));
        return errorHandler;
    }
}
