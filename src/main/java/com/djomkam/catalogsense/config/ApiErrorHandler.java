package com.djomkam.catalogsense.config;

import com.djomkam.catalogsense.search.SearchUnavailableException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 problem details. Spring MVC's own exceptions (404 for a missing
 * route, 405, 413 from {@code ResponseStatusException}, bad parameter types, ...) keep their status
 * through {@link ResponseEntityExceptionHandler}; anything unexpected becomes a generic 500.
 */
@RestControllerAdvice
@Slf4j
public class ApiErrorHandler extends ResponseEntityExceptionHandler {

    /** Invalid path variable or query parameter, e.g. a bad catalog id or {@code page=-1}. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        return ResponseEntity.badRequest().body(invalidParameter(e));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail invalidParameter(Exception e) {
        log.warn("Invalid request parameter: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameter: " + e.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        log.warn("Unreadable request body: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Body must be a JSON array of products. " + e.getMostSpecificCause().getMessage()));
    }

    /** Search is a secondary read model: say it is down instead of a generic 500. Ingestion keeps working. */
    @ExceptionHandler(SearchUnavailableException.class)
    public ProblemDetail searchUnavailable(SearchUnavailableException e) {
        log.warn("Search unavailable: {}", e.getCause() != null ? e.getCause().toString() : e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Search is temporarily unavailable. Catalog ingestion is not affected.");
        problem.setTitle("Search unavailable");
        return problem;
    }

    /** Last resort: log the details, but don't leak them to the caller. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpectedError(Exception e) {
        log.error("Unexpected error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side. Please try again later.");
    }
}
