package com.djomkam.catalogsense.outbox;

import com.djomkam.catalogsense.event.ProductEventType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores the event type as its wire value ("ProductUpserted"), so the column keeps the values it always had. */
@Converter
class ProductEventTypeConverter implements AttributeConverter<ProductEventType, String> {

    @Override
    public String convertToDatabaseColumn(ProductEventType type) {
        return type == null ? null : type.wireValue();
    }

    @Override
    public ProductEventType convertToEntityAttribute(String wireValue) {
        return wireValue == null ? null : ProductEventType.fromWireValue(wireValue);
    }
}
