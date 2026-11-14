package com.djomkam.catalogsense.search.query;

/** How results are ordered ({@code ?sort=PRICE_ASC}). Every order ends with the SKU, so pages are stable. */
public enum SortBy {
    RELEVANCE,
    PRICE_ASC,
    PRICE_DESC,
    NEWEST
}
