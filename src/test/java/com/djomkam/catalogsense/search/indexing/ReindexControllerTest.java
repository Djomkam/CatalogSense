package com.djomkam.catalogsense.search.indexing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReindexController.class, properties = "catalogsense.search.enabled=true")
class ReindexControllerTest {

    private static final ReindexJob RUNNING_JOB = ReindexJob.startedAt(Instant.parse("2026-11-14T12:00:00Z"));

    @Autowired MockMvc mvc;
    @MockitoBean SearchReindexService reindexService;

    @Test
    void startingAReindexAnswers202WithTheJobToPoll() throws Exception {
        when(reindexService.start()).thenReturn(RUNNING_JOB);

        mvc.perform(post("/api/search/reindex"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", endsWith("/api/search/reindex/" + RUNNING_JOB.id())))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void aSecondReindexWhileOneIsRunningIsAConflict() throws Exception {
        when(reindexService.start()).thenThrow(new ReindexAlreadyRunningException(RUNNING_JOB));

        mvc.perform(post("/api/search/reindex"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.id").value(RUNNING_JOB.id().toString()));
    }

    @Test
    void anUnknownJobIs404() throws Exception {
        when(reindexService.find(any(UUID.class))).thenReturn(Optional.empty());

        mvc.perform(get("/api/search/reindex/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
