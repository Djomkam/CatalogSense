package com.djomkam.catalogsense.ingestion;

public enum IngestionMode {
    /** The payload is the whole catalog: products missing from it are deleted. */
    FULL,
    /** The payload only contains changed products. */
    PARTIAL
}
