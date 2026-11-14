package com.djomkam.catalogsense.search.indexing;

import com.djomkam.catalogsense.search.ProductDocument;
import com.djomkam.catalogsense.search.ProductFields;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the JSON mapping, the document record and the field-name constants in step. */
class ProductIndexDefinitionTest {

    @Test
    void theResourceParsesIntoTheClientTypes() {
        ProductIndexDefinition definition = ProductIndexDefinition.load(new JacksonJsonpMapper());

        assertThat(definition.mappings().properties()).containsKeys(ProductFields.CATALOG_ID, ProductFields.PRICE);
        assertThat(definition.settings().analysis().analyzer()).containsKey("folding");
    }

    @Test
    void mappingDeclaresExactlyTheDocumentFields() throws Exception {
        JsonNode mapping = new ObjectMapper()
                .readTree(getClass().getResourceAsStream(ProductIndexDefinition.RESOURCE))
                .at("/mappings/properties");
        Set<String> mappedFields = new HashSet<>();
        mapping.fieldNames().forEachRemaining(mappedFields::add);

        Set<String> documentFields = new HashSet<>(Arrays.stream(ProductDocument.class.getRecordComponents())
                .map(RecordComponent::getName).toList());

        assertThat(mappedFields).isEqualTo(documentFields);
    }

    @Test
    void everyFieldConstantExistsInTheMapping() throws Exception {
        JsonNode mapping = new ObjectMapper()
                .readTree(getClass().getResourceAsStream(ProductIndexDefinition.RESOURCE))
                .at("/mappings/properties");

        for (Field constant : ProductFields.class.getDeclaredFields()) {
            if (!Modifier.isStatic(constant.getModifiers()) || constant.getType() != String.class) {
                continue;
            }
            String path = "/" + ((String) constant.get(null)).replace(".", "/fields/");
            assertThat(mapping.at(path).isMissingNode())
                    .as("%s (%s) is in the mapping", constant.getName(), path)
                    .isFalse();
        }
    }
}
