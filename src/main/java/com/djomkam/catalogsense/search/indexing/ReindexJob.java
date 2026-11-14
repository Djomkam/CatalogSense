package com.djomkam.catalogsense.search.indexing;

import java.time.Instant;
import java.util.UUID;

/**
 * Progress of a search reindex, as returned by {@code /api/search/reindex}. Immutable: each step
 * of the job replaces it with an updated copy.
 *
 * @param targetIndex physical index being built (null until it is created)
 * @param indexed     products written to the new index so far
 * @param rejected    products OpenSearch refused; any rejection cancels the alias switch
 * @param error       why the job failed (null unless {@link Status#FAILED})
 */
public record ReindexJob(
        UUID id,
        Status status,
        Instant startedAt,
        Instant finishedAt,
        String targetIndex,
        long indexed,
        long rejected,
        String error
) {

    public enum Status { RUNNING, SUCCEEDED, FAILED }

    static ReindexJob startedAt(Instant now) {
        return new ReindexJob(UUID.randomUUID(), Status.RUNNING, now, null, null, 0, 0, null);
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    ReindexJob buildingInto(String index) {
        return new ReindexJob(id, status, startedAt, finishedAt, index, indexed, rejected, error);
    }

    ReindexJob addProgress(long newlyIndexed, long newlyRejected) {
        return new ReindexJob(id, status, startedAt, finishedAt, targetIndex,
                indexed + newlyIndexed, rejected + newlyRejected, error);
    }

    ReindexJob succeededAt(Instant now) {
        return new ReindexJob(id, Status.SUCCEEDED, startedAt, now, targetIndex, indexed, rejected, null);
    }

    ReindexJob failedAt(Instant now, String reason) {
        return new ReindexJob(id, Status.FAILED, startedAt, now, targetIndex, indexed, rejected, reason);
    }
}
