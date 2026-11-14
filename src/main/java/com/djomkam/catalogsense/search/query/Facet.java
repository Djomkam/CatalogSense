package com.djomkam.catalogsense.search.query;

import com.djomkam.catalogsense.search.ProductFields;

/**
 * The facets a shopper can filter on. Each one drives its filter, its aggregation and its entry in the
 * response, so adding a facet means adding a constant here (and a selection to the criteria).
 *
 * @param apiName    name in the query string ({@code ?color=Red}) and in the response's {@code facets}
 * @param indexField keyword field in the index that holds the values
 */
public enum Facet {

    BRAND("brand", ProductFields.BRAND),
    CATEGORY("category", ProductFields.CATEGORY),
    COLOR("color", ProductFields.COLORS),
    SIZE("size", ProductFields.SIZES);

    private final String apiName;
    private final String indexField;

    Facet(String apiName, String indexField) {
        this.apiName = apiName;
        this.indexField = indexField;
    }

    public String apiName() {
        return apiName;
    }

    public String indexField() {
        return indexField;
    }
}
