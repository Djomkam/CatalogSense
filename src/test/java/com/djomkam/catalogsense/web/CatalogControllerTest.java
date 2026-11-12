package com.djomkam.catalogsense.web;

import com.djomkam.catalogsense.domain.HealthIssueRepository;
import com.djomkam.catalogsense.domain.ProductRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired HealthIssueRepository issues;

    @BeforeEach
    void clean() {
        issues.deleteAll();
        products.deleteAll();
    }

    @Test
    void sampleCatalogEndToEnd() throws Exception {
        String full = new ClassPathResource("sample-catalog-full.json").getContentAsString(StandardCharsets.UTF_8);

        mvc.perform(put("/api/catalogs/demo/products").contentType(MediaType.APPLICATION_JSON).content(full))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(12))
                .andExpect(jsonPath("$.accepted").value(8))
                .andExpect(jsonPath("$.rejected").value(4));

        mvc.perform(get("/api/catalogs/demo/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productsIndexed").value(8))
                .andExpect(jsonPath("$.skusWithErrors").value(3));

        mvc.perform(get("/api/catalogs/demo/health/issues").param("severity", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].severity").value("ERROR"));

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
    void nonArrayBodyIs400() throws Exception {
        mvc.perform(patch("/api/catalogs/demo/products").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }
}
