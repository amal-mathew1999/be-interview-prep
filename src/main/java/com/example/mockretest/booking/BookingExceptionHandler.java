package com.example.mockretest.booking;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
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

/** Maps booking errors to RFC 9457 {@code application/problem+json} responses. */
@RestControllerAdvice(basePackageClasses = BookingController.class)
public class BookingExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BookingExceptionHandler.class);
    private static final URI CONCURRENT_UPDATE_TYPE = URI.create("urn:problem-type:booking:concurrent-update");
    private static final URI BLANK_TYPE = URI.create("about:blank");
    private static final String FRAMEWORK_TYPE_PREFIX = "urn:problem-type:booking:http-";
    private static final URI INTERNAL_ERROR_TYPE = URI.create("urn:problem-type:booking:internal-error");

    @ExceptionHandler(BookingProblemException.class)
    public ResponseEntity<ProblemDetail> handleBookingProblem(BookingProblemException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setType(ex.getType());
        problem.setTitle(ex.getTitle());
        return respond(problem, request);
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ProblemDetail> handleConcurrentUpdate(
            ConcurrencyFailureException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The booking was changed concurrently; retry the request");
        problem.setType(CONCURRENT_UPDATE_TYPE);
        problem.setTitle("Concurrent update");
        return respond(problem, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled booking error", ex);
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setType(INTERNAL_ERROR_TYPE);
        problem.setTitle("Internal Server Error");
        return respond(problem, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = ex.getBody();
        problem.setDetail("Request validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            if (problem.getInstance() == null && request instanceof ServletWebRequest servletRequest) {
                problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
            }
            if (problem.getType() == null || BLANK_TYPE.equals(problem.getType())) {
                problem.setType(URI.create(FRAMEWORK_TYPE_PREFIX + statusCode.value()));
            }
        }
        return response;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem, HttpServletRequest request) {
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
