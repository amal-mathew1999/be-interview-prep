package com.example.mockretest.files;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice(basePackageClasses = FilesController.class)
public class FilesExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String PROBLEM_TYPE_PREFIX = "urn:problem-type:files:";
    private static final URI BLANK_TYPE = URI.create("about:blank");
    private static final Logger LOG = LoggerFactory.getLogger(FilesExceptionHandler.class);

    // ObjectProvider, not FilesProperties: every @WebMvcTest slice in the app picks up this advice, but not the files
    // configuration, so a hard dependency would break other features' slice tests.
    private final ObjectProvider<FilesProperties> properties;

    public FilesExceptionHandler(ObjectProvider<FilesProperties> properties) {
        this.properties = properties;
    }

    @ExceptionHandler(FilesNotFoundException.class)
    ProblemDetail handleNotFound(FilesNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "File not found", ex.getMessage());
    }

    @ExceptionHandler(FilesUnsupportedTypeException.class)
    ProblemDetail handleUnsupportedType(FilesUnsupportedTypeException ex) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type", ex.getMessage());
    }

    @ExceptionHandler(FilesEmptyFileException.class)
    ProblemDetail handleEmpty(FilesEmptyFileException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Empty file", ex.getMessage());
    }

    @ExceptionHandler(FilesTooLargeException.class)
    ProblemDetail handleTooLarge(FilesTooLargeException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = problem(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", tooLargeDetail());
        return handleExceptionInternal(ex, body, headers, HttpStatus.PAYLOAD_TOO_LARGE, request);
    }

    /**
     * A non-multipart {@code POST /api/files} reaches the handler (no {@code consumes} restriction) and fails while
     * resolving the {@code file} part: answer 415. A multipart body that cannot be parsed is a 400. Size violations
     * ({@link MaxUploadSizeExceededException}) are more specific and handled above.
     */
    private String tooLargeDetail() {
        FilesProperties files = properties.getIfAvailable();
        if (files == null || files.maxSize() == null) {
            return "The uploaded file exceeds the maximum allowed size";
        }
        return new FilesTooLargeException(FilesService.describe(files.maxSize())).getMessage();
    }

    @ExceptionHandler(MultipartException.class)
    ProblemDetail handleMultipart(MultipartException ex, HttpServletRequest request) {
        String contentType = request.getContentType();
        boolean multipart =
                contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
        if (!multipart) {
            return problem(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported media type",
                    "Uploads must be sent as multipart/form-data with the file in a part named 'file'");
        }
        return problem(
                HttpStatus.BAD_REQUEST, "Malformed multipart request", "The multipart request could not be parsed");
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
