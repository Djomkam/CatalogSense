package com.djomkam.catalogsense;

import com.djomkam.catalogsense.catalog.ProductRepository;
import com.djomkam.catalogsense.health.HealthIssueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The ingestion, product and health endpoints together, on the sample catalog. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogApiEndToEndTest {

    @Autowired MockMvc mvc;
    @Autowired ProductRepository productRepository;
    @Autowired HealthIssueRepository healthIssueRepository;

    @BeforeEach
    void startFromEmptyTables() {
        healthIssueRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void sampleCatalogEndToEnd() throws Exception {
        String fullCatalog = new ClassPathResource("sample-catalog-full.json").getContentAsString(StandardCharsets.UTF_8);

        mvc.perform(put("/api/catalogs/demo/products").contentType(MediaType.APPLICATION_JSON).content(fullCatalog))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("FULL"))
                .andExpect(jsonPath("$.received").value(12))
                .andExpect(jsonPath("$.accepted").value(8))
                .andExpect(jsonPath("$.rejected").value(4));

        mvc.perform(get("/api/catalogs/demo/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCount").value(8))
                .andExpect(jsonPath("$.skusWithErrors").value(3))
                .andExpect(jsonPath("$.issuesByRule[0].severity").exists());

        mvc.perform(get("/api/catalogs/demo/health/issues").param("severity", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].severity").value("ERROR"))
                .andExpect(jsonPath("$.page.totalElements").isNumber());

        mvc.perform(get("/api/catalogs/demo/health/issues").param("rule", "PRICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rule").value("PRICE"));

        mvc.perform(get("/api/catalogs/demo/health/issues").param("rule", "PRICE").param("severity", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rule").value("PRICE"))
                .andExpect(jsonPath("$.content[0].severity").value("ERROR"));

        mvc.perform(get("/api/catalogs/demo/products/TR-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants.length()").value(3));

        mvc.perform(get("/api/catalogs/demo/products").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.page.size").value(5))
                .andExpect(jsonPath("$.page.totalElements").value(8));
    }

    @Test
    void unknownProductIs404() throws Exception {
        mvc.perform(get("/api/catalogs/demo/products/NOPE")).andExpect(status().isNotFound());
    }

    @Test
    void invalidCatalogIdIs400() throws Exception {
        mvc.perform(get("/api/catalogs/bad id!/health")).andExpect(status().isBadRequest());
    }

    @Test
    void unknownSeverityIs400() throws Exception {
        mvc.perform(get("/api/catalogs/demo/health/issues").param("severity", "FATAL"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonArrayBodyIs400() throws Exception {
        mvc.perform(patch("/api/catalogs/demo/products").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }
}
