package com.djomkam.catalogsense.search.indexing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.stream.JsonParser;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.IndexSettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;

/**
 * Settings and mapping of the product index, read from {@code opensearch/product-index.json}.
 *
 * <p>The JSON is the same body you would send to {@code PUT /<index>} by hand, so it can be read and
 * reviewed without knowing the Java client. In short: strict mapping (unknown fields are rejected),
 * a {@code folding} analyzer (lowercase + asciifolding, so "echarpe" matches "Écharpe"), keyword fields
 * for filters and facets with a {@code .text} sub-field for full-text search, and the price as a
 * scaled_float in cents.</p>
 */
record ProductIndexDefinition(IndexSettings settings, TypeMapping mappings) {

    static final String RESOURCE = "/opensearch/product-index.json";

    /** Parses the resource with the client's own deserializers, so a typo fails at startup, not at index time. */
    static ProductIndexDefinition load(JsonpMapper jsonpMapper) {
        try (InputStream json = ProductIndexDefinition.class.getResourceAsStream(RESOURCE)) {
            if (json == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            JsonNode definition = new ObjectMapper().readTree(json);
            return new ProductIndexDefinition(
                    parse(definition.required("settings"), IndexSettings._DESERIALIZER, jsonpMapper),
                    parse(definition.required("mappings"), TypeMapping._DESERIALIZER, jsonpMapper));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }

    private static <T> T parse(JsonNode section, JsonpDeserializer<T> deserializer, JsonpMapper jsonpMapper) {
        try (JsonParser parser = jsonpMapper.jsonProvider().createParser(new StringReader(section.toString()))) {
            return deserializer.deserialize(parser, jsonpMapper);
        }
    }
}
