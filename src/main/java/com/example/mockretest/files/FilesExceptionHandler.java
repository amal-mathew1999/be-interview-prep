package com.example.mockretest.files;

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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(basePackageClasses = FilesController.class)
public class FilesExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String PROBLEM_TYPE_PREFIX = "urn:problem-type:files:";
    private static final URI BLANK_TYPE = URI.create("about:blank");
    private static final Logger LOG = LoggerFactory.getLogger(FilesExceptionHandler.class);

    private final FilesProperties properties;

    public FilesExceptionHandler(FilesProperties properties) {
        this.properties = properties;
    }

    @ExceptionHandler(StoredFileNotFoundException.class)
    ProblemDetail handleNotFound(StoredFileNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "File not found", ex.getMessage());
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    ProblemDetail handleUnsupportedType(UnsupportedFileTypeException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type", ex.getMessage());
    }

    @ExceptionHandler(EmptyFileException.class)
    ProblemDetail handleEmpty(EmptyFileException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Empty file", ex.getMessage());
    }

    @ExceptionHandler(FileTooLargeException.class)
    ProblemDetail handleTooLarge(FileTooLargeException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "File too large",
                new FileTooLargeException(FilesService.describe(properties.maxSize())).getMessage());
        return handleExceptionInternal(ex, body, headers, HttpStatus.PAYLOAD_TOO_LARGE, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        LOG.error("Unexpected error in files API", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            withType(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return withType(problem);
    }

    /** Gives every problem an explicit, stable {@code type} so it is always present in the JSON body. */
    private static ProblemDetail withType(ProblemDetail problem) {
        if (problem.getType() == null || BLANK_TYPE.equals(problem.getType())) {
            problem.setType(URI.create(PROBLEM_TYPE_PREFIX + problem.getStatus()));
        }
        return problem;
    }
}
