package com.djomkam.catalogsense.search.indexing;

/** A reindex was requested while another one is still running. */
public class ReindexAlreadyRunningException extends RuntimeException {

    private final transient ReindexJob runningJob;

    public ReindexAlreadyRunningException(ReindexJob runningJob) {
        super("A reindex is already running: " + runningJob.id());
        this.runningJob = runningJob;
    }

    public ReindexJob runningJob() {
        return runningJob;
    }
}
