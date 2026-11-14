package com.djomkam.catalogsense.search;

/**
 * Field names of the product index, in one place for the code that writes, queries and parses it.
 *
 * <p>They must match the property names in {@code opensearch/product-index.json} and the components of
 * {@link ProductDocument} (which Jackson serializes as-is); {@code ProductIndexDefinitionTest} checks both.</p>
 */
public final class ProductFields {

    public static final String CATALOG_ID = "catalogId";
    public static final String SKU = "sku";
    public static final String NAME = "name";
    public static final String BRAND = "brand";
    public static final String CATEGORY = "category";
    public static final String COLORS = "colors";
    public static final String SIZES = "sizes";
    public static final String PRICE = "price";
    public static final String IN_STOCK = "inStock";
    public static final String UPDATED_AT = "updatedAt";

    /** Full-text sub-fields of keyword fields (see the "text" sub-field in the mapping). */
    public static final String BRAND_TEXT = BRAND + ".text";
    public static final String CATEGORY_TEXT = CATEGORY + ".text";
    public static final String COLORS_TEXT = COLORS + ".text";

    private ProductFields() {
    }
}
