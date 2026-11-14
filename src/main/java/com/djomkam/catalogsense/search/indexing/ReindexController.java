package com.djomkam.catalogsense.search.indexing;

import com.djomkam.catalogsense.search.ConditionalOnSearchEnabled;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

/**
 * Admin endpoint to rebuild the search index from PostgreSQL (see {@link SearchReindexService}).
 * It covers every catalog, because all catalogs share one index.
 */
@RestController
@RequestMapping("/api/search/reindex")
@RequiredArgsConstructor
@ConditionalOnSearchEnabled
public class ReindexController {

    private final SearchReindexService reindexService;

    /** Starts a rebuild: 202 Accepted, with the job to poll in the body and the Location header. */
    @PostMapping
    public ResponseEntity<ReindexJob> startReindex() {
        ReindexJob job = reindexService.start();
        var jobLocation = ServletUriComponentsBuilder.fromCurrentRequest().path("/{jobId}").buildAndExpand(job.id());
        return ResponseEntity.accepted().location(jobLocation.toUri()).body(job);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ReindexJob> reindexStatus(@PathVariable UUID jobId) {
        return ResponseEntity.of(reindexService.find(jobId));
    }

    /** 409 with the job that is still running, so the caller can poll it instead. */
    @ExceptionHandler(ReindexAlreadyRunningException.class)
    public ResponseEntity<ReindexJob> alreadyRunning(ReindexAlreadyRunningException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.runningJob());
    }
}
