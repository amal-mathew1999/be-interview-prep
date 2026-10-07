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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Renders every error that reaches the servlet container's error path (405, unmapped 404, eager multipart limits,
 * uncaught exceptions, {@code response.sendError}) as an RFC 9457 {@code application/problem+json} body. Replaces
 * Boot's {@code BasicErrorController}.
 */
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
public class CommonProblemDetailErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(CommonProblemDetailErrorController.class);

    static final String GENERIC_SERVER_DETAIL = "An unexpected error occurred. Please try again later.";
    static final String UPLOAD_TOO_LARGE_DETAIL = "The request exceeds the maximum allowed upload size.";
    static final String CONTENT_TOO_LARGE_DETAIL = "The request content is too large.";
    static final String CLIENT_ERROR_TITLE = "Client Error";
    static final String SERVER_ERROR_TITLE = "Server Error";

    private final ErrorAttributes errorAttributes;

    public CommonProblemDetailErrorController(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping
    public ResponseEntity<ProblemDetail> error(HttpServletRequest request, HttpServletResponse response) {
        HttpStatusCode status = resolveStatus(request);
        Throwable error = errorAttributes.getError(new ServletWebRequest(request));
        String path = resolvePath(request);
        String method = resolveMethod(request, error);

        if (!status.is4xxClientError()) {
            logServerError(request, method, path, status, error);
        }

        if (response.isCommitted()) {
            // Part of the body was already sent (e.g. streaming); the container includes this error page into it, so
            // writing a problem body would append JSON to a partial payload. Send nothing more.
            log.warn(
                    "Cannot render problem for {} {} (status {}): response already committed",
                    method,
                    path,
                    status.value());
            return ResponseEntity.status(status).build();
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, resolveDetail(status, error, method, path));
        problem.setType(CommonProblemTypes.forStatus(status.value()));
        problem.setTitle(resolveTitle(status));
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

    /**
     * Exceptions that propagated to the container (exposed as {@link RequestDispatcher#ERROR_EXCEPTION}) were already
     * logged with their stack trace by the container, so the throwable is only logged here when Spring resolved it.
     */
    private static void logServerError(
            HttpServletRequest request, String method, String path, HttpStatusCode status, Throwable error) {
        boolean loggedByContainer = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) != null;
        if (error != null && !loggedByContainer) {
            log.error("Request {} {} failed with status {}", method, path, status.value(), error);
        } else {
            log.error("Request {} {} failed with status {}", method, path, status.value());
        }
    }

    private static HttpStatusCode resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer value) {
            if (value >= 400 && value <= 999) {
                return HttpStatusCode.valueOf(value);
            }
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        // Direct request to the error path without an error dispatch.
        return HttpStatus.NOT_FOUND;
    }

    /**
     * The error dispatch is made with the container's error-page method (GET), so the client's method is taken from
     * {@link RequestDispatcher#ERROR_METHOD} (Servlet 6.1), which the container sets for every error dispatch, then from
     * the original exception when it carries one.
     */
    private static String resolveMethod(HttpServletRequest request, Throwable error) {
        Object original = request.getAttribute(RequestDispatcher.ERROR_METHOD);
        if (original instanceof String value && !value.isBlank()) {
            return value;
        }
        if (error instanceof HttpRequestMethodNotSupportedException ex) {
            return ex.getMethod();
        }
        if (error instanceof NoResourceFoundException ex) {
            return ex.getHttpMethod().name();
        }
        if (error instanceof NoHandlerFoundException ex) {
            return ex.getHttpMethod();
        }
        return request.getMethod();
    }

    private static String resolveTitle(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        if (known != null) {
            return known.getReasonPhrase();
        }
        return status.is4xxClientError() ? CLIENT_ERROR_TITLE : SERVER_ERROR_TITLE;
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

    private static String resolveDetail(HttpStatusCode status, Throwable error, String method, String path) {
        if (!status.is4xxClientError()) {
            return GENERIC_SERVER_DETAIL;
        }
        if (error instanceof MaxUploadSizeExceededException) {
            return UPLOAD_TOO_LARGE_DETAIL;
        }
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return "No resource found for " + method + " " + path + ".";
        }
        if (status.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return methodNotAllowedDetail(method, error);
        }
        if (status.isSameCodeAs(HttpStatus.CONTENT_TOO_LARGE)) {
            return CONTENT_TOO_LARGE_DETAIL;
        }
        return clientErrorDetail(status, error);
    }

    private static String methodNotAllowedDetail(String method, Throwable error) {
        String detail = "Method " + method + " is not supported for this resource.";
        if (error instanceof ErrorResponse errorResponse) {
            List<String> allow = errorResponse.getHeaders().getOrEmpty(HttpHeaders.ALLOW);
            if (!allow.isEmpty()) {
                return detail + " Supported methods: " + String.join(", ", allow) + ".";
            }
        }
        return detail;
    }

    private static String clientErrorDetail(HttpStatusCode status, Throwable error) {
        // Framework ErrorResponse details are written for clients; arbitrary exception/sendError messages are not.
        if (error instanceof ErrorResponse errorResponse) {
            String detail = errorResponse.getBody().getDetail();
            if (detail != null && !detail.isBlank()) {
                return detail;
            }
        }
        return "The request could not be completed (" + status.value() + " " + resolveTitle(status) + ").";
    }
}
