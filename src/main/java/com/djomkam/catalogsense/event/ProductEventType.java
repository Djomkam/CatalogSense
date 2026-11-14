package com.djomkam.catalogsense.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Kind of change in a product event. The wire value ("ProductUpserted", ...) is what goes into the
 * Kafka message, the {@code eventType} header and the {@code outbox_event.event_type} column.
 */
public enum ProductEventType {

    UPSERTED("ProductUpserted"),
    DELETED("ProductDeleted"),
    /** A type this version doesn't know (sent by a newer producer); consumers skip it. */
    UNKNOWN("Unknown");

    private final String wireValue;

    ProductEventType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static ProductEventType fromWireValue(String wireValue) {
        for (ProductEventType type : values()) {
            if (type.wireValue.equals(wireValue)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
