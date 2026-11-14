package com.djomkam.catalogsense.outbox;

import com.djomkam.catalogsense.event.ProductEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One event waiting to be (or already) published to Kafka. Written in the same transaction as the change. */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique id sent with the message, so consumers can de-duplicate (delivery is at-least-once). */
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Convert(converter = ProductEventTypeConverter.class)
    @Column(name = "event_type", nullable = false, updatable = false)
    private ProductEventType eventType;

    @Column(name = "catalog_id", nullable = false, updatable = false)
    private String catalogId;

    /** Kafka key: catalogId:sku, so every change of one product lands on the same partition, in order. */
    @Column(name = "message_key", nullable = false, updatable = false)
    private String messageKey;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    public OutboxEvent(String eventId, ProductEventType eventType, String catalogId, String messageKey,
                       String payload, Instant createdAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.catalogId = catalogId;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public void markPublished(Instant publishedAt) {
        this.attempts++;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.attempts++;
        this.lastError = truncate(error);
    }

    private static String truncate(String error) {
        if (error == null || error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }
}
