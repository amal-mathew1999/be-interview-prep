package com.example.mockretest.common.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Renders every error that reaches the servlet container's error path (405, unmapped 404, eager multipart limits,
 * uncaught exceptions, {@code response.sendError}) as an RFC 9457 {@code application/problem+json} body. Replaces
 * Boot's {@code BasicErrorController}.
 */
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
public class ProblemDetailErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailErrorController.class);

    static final String GENERIC_SERVER_DETAIL = "An unexpected error occurred. Please try again later.";
    static final String UPLOAD_TOO_LARGE_DETAIL = "The request exceeds the maximum allowed upload size.";

    private final ErrorAttributes errorAttributes;

    public ProblemDetailErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping
    public ResponseEntity<ProblemDetail> error(HttpServletRequest request, HttpServletResponse response) {
        HttpStatus status = resolveStatus(request);
        Throwable error = errorAttributes.getError(new ServletWebRequest(request));
        String path = resolvePath(request);

        if (status.is5xxServerError()) {
            log.error("Request {} {} failed with status {}", request.getMethod(), path, status.value(), error);
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, resolveDetail(status, error, request, path));
        problem.setType(CommonProblemTypes.forStatus(status.value()));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(path));

        HttpHeaders headers = new HttpHeaders();
        if (error instanceof ErrorResponse errorResponse) {
            errorResponse.getHeaders().forEach((name, values) -> {
                if (!response.containsHeader(name)) {
                    headers.addAll(name, values);
                }
            });
        }
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static HttpStatus resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer value) {
            HttpStatus status = HttpStatus.resolve(value);
            if (status != null && status.isError()) {
                return status;
            }
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        // Direct request to the error path without an error dispatch.
        return HttpStatus.NOT_FOUND;
    }

    private static String resolvePath(HttpServletRequest request) {
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = uri instanceof String value && !value.isBlank() ? value : request.getRequestURI();
        try {
            return URI.create(path).toString();
        } catch (IllegalArgumentException ex) {
            return "/";
        }
    }

    private static String resolveDetail(HttpStatus status, Throwable error, HttpServletRequest request, String path) {
        if (status.is5xxServerError()) {
            return GENERIC_SERVER_DETAIL;
        }
        return switch (status) {
            case NOT_FOUND -> "No resource found for " + request.getMethod() + " " + path + ".";
            case METHOD_NOT_ALLOWED -> methodNotAllowedDetail(request, error);
            case CONTENT_TOO_LARGE -> UPLOAD_TOO_LARGE_DETAIL;
            default -> clientErrorDetail(status, error);
        };
    }

    private static String methodNotAllowedDetail(HttpServletRequest request, Throwable error) {
        String detail = "Method " + request.getMethod() + " is not supported for this resource.";
        if (error instanceof ErrorResponse errorResponse) {
            List<String> allow = errorResponse.getHeaders().getOrEmpty(HttpHeaders.ALLOW);
            if (!allow.isEmpty()) {
                return detail + " Supported methods: " + String.join(", ", allow) + ".";
            }
        }
        return detail;
    }

    private static String clientErrorDetail(HttpStatus status, Throwable error) {
        if (error instanceof MaxUploadSizeExceededException) {
            return UPLOAD_TOO_LARGE_DETAIL;
        }
        // Framework ErrorResponse details are written for clients; arbitrary exception/sendError messages are not.
        if (error instanceof ErrorResponse errorResponse) {
            String detail = errorResponse.getBody().getDetail();
            if (detail != null && !detail.isBlank()) {
                return detail;
            }
        }
        return "The request could not be completed (" + status.value() + " " + status.getReasonPhrase() + ").";
    }
}
