package com.djomkam.catalogsense.search;

/**
 * An OpenSearch call failed: the cluster is unreachable or answered with an error.
 *
 * <p>The search endpoint turns it into a 503 (see {@code ApiErrorHandler}). The Kafka consumer lets it
 * propagate, so the message is retried and then sent to the dead-letter topic.</p>
 */
public class SearchUnavailableException extends RuntimeException {

    public SearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public SearchUnavailableException(String message) {
        super(message);
    }
}
