package com.example.mockretest.library;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Maps every error raised by the library API to an RFC 9457 application/problem+json body. */
@RestControllerAdvice(basePackageClasses = LibraryController.class)
public class LibraryExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LibraryExceptionHandler.class);

    /** Problem types are URNs so they are always serialized (Spring omits the default "about:blank"). */
    static final String PROBLEM_TYPE_PREFIX = "urn:problem-type:library:";

    private static final URI DEFAULT_TYPE = URI.create("about:blank");

    @ExceptionHandler(BookNotFoundException.class)
    ResponseEntity<Object> handleNotFound(BookNotFoundException ex, WebRequest request) {
        return problem(ex, HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler({DuplicateIsbnException.class, BookUnavailableException.class, BookNotBorrowedException.class})
    ResponseEntity<Object> handleConflict(RuntimeException ex, WebRequest request) {
        return problem(ex, HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation in library API", ex);
        return problem(ex, HttpStatus.CONFLICT, "The request conflicts with existing data.", request);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<Object> handleConcurrency(ConcurrencyFailureException ex, WebRequest request) {
        log.info("Concurrent modification in library API: {}", ex.getMessage());
        return problem(
                ex, HttpStatus.CONFLICT, "The book was modified concurrently; reload it and try again.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error in library API", ex);
        return problem(ex, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), String.valueOf(error.getDefaultMessage()));
        }
        ProblemDetail body = ex.getBody();
        body.setType(URI.create(PROBLEM_TYPE_PREFIX + "validation-error"));
        body.setDetail("Validation failed for one or more fields.");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** Ensures every problem body, including framework-generated ones, carries a type and the request path. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail detail
                ? detail
                : ProblemDetail.forStatusAndDetail(statusCode, "The request could not be processed.");
        if (problem.getType() == null || DEFAULT_TYPE.equals(problem.getType())) {
            problem.setType(URI.create(PROBLEM_TYPE_PREFIX + typeSlug(statusCode)));
        }
        if (problem.getInstance() == null && request instanceof ServletWebRequest servletRequest) {
            problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
        }
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private static String typeSlug(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null
                ? String.valueOf(statusCode.value())
                : status.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private ResponseEntity<Object> problem(Exception ex, HttpStatus status, String detail, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        return handleExceptionInternal(ex, body, new HttpHeaders(), status, request);
    }
}
