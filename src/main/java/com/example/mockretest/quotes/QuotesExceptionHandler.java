package com.example.mockretest.quotes;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions raised while serving {@code /api/quotes/**} to RFC 9457 problem+json. Every body carries an explicit
 * {@code type}; unexpected failures become a generic {@code 500} that never exposes internal details.
 */
@RestControllerAdvice(basePackageClasses = QuoteController.class)
public class QuotesExceptionHandler extends ResponseEntityExceptionHandler {

    static final URI INTERNAL_ERROR_TYPE = URI.create("urn:problem-type:quotes:internal-error");
    static final String TYPE_PREFIX = "urn:problem-type:quotes:http-";
    static final String INTERNAL_ERROR_DETAIL = "An unexpected error occurred.";

    private static final Logger LOG = LoggerFactory.getLogger(QuotesExceptionHandler.class);
    private static final URI ABOUT_BLANK = URI.create("about:blank");

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        LOG.error("Unhandled exception while serving quotes", ex);
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_DETAIL);
        problem.setType(INTERNAL_ERROR_TYPE);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /** Gives framework-generated problems (405, 406, ...) an explicit, status-specific {@code type}. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem
                && (problem.getType() == null || ABOUT_BLANK.equals(problem.getType()))) {
            problem.setType(URI.create(TYPE_PREFIX + statusCode.value()));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
