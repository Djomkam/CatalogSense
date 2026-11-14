package com.djomkam.catalogsense.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.opensearch.client.opensearch.OpenSearchClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Builds the real client (as the app does) and sample documents for the search tests. */
public final class OpenSearchTestSupport {

    public static final Instant UPDATED_AT = Instant.parse("2026-11-13T00:00:00Z");

    private OpenSearchTestSupport() {
    }

    public static SearchProperties properties(String url, String indexAlias) {
        return new SearchProperties(true, url, indexAlias, new SearchProperties.Consumer("test", 0, Duration.ZERO));
    }

    public static OpenSearchClient client(SearchProperties properties) {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            var config = new OpenSearchConfig();
            return config.openSearchClient(config.openSearchTransport(properties, objectMapper));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void close(OpenSearchClient client) {
        try {
            client._transport().close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ProductDocument jacket(String catalogId, String sku, long version) {
        return new ProductDocument(catalogId, sku, "Trail Jacket", "Acme", "Jackets", new BigDecimal("129.99"), "CAD",
                null, true, version, UPDATED_AT, List.of("Red"), List.of("M"));
    }
}
