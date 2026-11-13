package com.djomkam.catalogsense.search;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SearchController.class, properties = "catalogsense.search.enabled=true")
class SearchControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SearchService searchService;
    @MockitoBean SearchReindexService reindexService;

    @Test
    void mapsQueryParametersToCriteria() throws Exception {
        when(searchService.search(any())).thenReturn(new SearchResult(1, 1, 10, List.of(
                new SearchResult.Hit("A", "Trail Jacket", "Acme", "Jackets", new BigDecimal("99.00"), "CAD", null,
                        true, List.of("Red"), List.of("M"), 1.5)),
                Map.of("brand", List.of(new SearchResult.FacetValue("Acme", 1))), null));

        mvc.perform(get("/api/catalogs/demo/search")
                        .param("q", "jacket").param("brand", "Acme", "Zest").param("color", "Red")
                        .param("inStock", "true").param("maxPrice", "150").param("sort", "PRICE_DESC")
                        .param("page", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits[0].sku").value("A"))
                .andExpect(jsonPath("$.facets.brand[0].count").value(1));

        ArgumentCaptor<ProductSearchCriteria> captor = ArgumentCaptor.forClass(ProductSearchCriteria.class);
        verify(searchService).search(captor.capture());
        ProductSearchCriteria c = captor.getValue();
        assertThat(c.catalogId()).isEqualTo("demo");
        assertThat(c.text()).isEqualTo("jacket");
        assertThat(c.brands()).containsExactly("Acme", "Zest");
        assertThat(c.colors()).containsExactly("Red");
        assertThat(c.inStock()).isTrue();
        assertThat(c.maxPrice()).isEqualByComparingTo("150");
        assertThat(c.sort()).isEqualTo(ProductSearchCriteria.SortOrder.PRICE_DESC);
        assertThat(c.page()).isEqualTo(1);
        assertThat(c.pageSize()).isEqualTo(10);
    }

    @Test
    void rejectsPagesBeyondTheResultWindow() throws Exception {
        mvc.perform(get("/api/catalogs/demo/search").param("page", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openSearchDownGives503() throws Exception {
        when(searchService.search(any())).thenThrow(new UncheckedIOException(new IOException("Connection refused")));

        mvc.perform(get("/api/catalogs/demo/search").param("q", "x"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Search unavailable"));
    }
}
