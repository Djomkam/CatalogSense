package com.djomkam.catalogsense.search;

import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.transport.httpclient5.ResponseException;

/**
 * Reads failed OpenSearch calls. Depending on the endpoint and the response body, the client reports an
 * error status either as an {@link OpenSearchException} (parsed error) or as a transport
 * {@link ResponseException} (raw response), so callers should not have to check both.
 */
public final class OpenSearchErrors {

    private static final int CONFLICT = 409;
    private static final int NOT_FOUND = 404;
    private static final String ALREADY_EXISTS = "resource_already_exists_exception";

    private OpenSearchErrors() {
    }

    /** HTTP status of the failed call, or -1 when there was no response (connection refused, timeout). */
    public static int httpStatus(Exception error) {
        if (error instanceof OpenSearchException openSearchError) {
            return openSearchError.status();
        }
        if (error instanceof ResponseException responseError) {
            return responseError.status();
        }
        return -1;
    }

    /** A versioned write was refused because the index already holds this version or a newer one. */
    public static boolean isVersionConflict(Exception error) {
        return httpStatus(error) == CONFLICT;
    }

    public static boolean isNotFound(Exception error) {
        return httpStatus(error) == NOT_FOUND;
    }

    /** Index creation failed because the index exists (typically created by another instance meanwhile). */
    public static boolean isAlreadyExists(Exception error) {
        if (error instanceof OpenSearchException openSearchError) {
            return ALREADY_EXISTS.equals(openSearchError.error().type());
        }
        // A raw ResponseException only carries the body in its message.
        return error instanceof ResponseException && String.valueOf(error.getMessage()).contains(ALREADY_EXISTS);
    }
}
