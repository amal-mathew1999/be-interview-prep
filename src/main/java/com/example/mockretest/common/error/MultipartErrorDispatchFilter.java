package com.example.mockretest.common.error;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Hides the multipart content type of the original request during the container's error dispatch.
 *
 * <p>When multipart parsing fails (e.g. {@code max-file-size} exceeded) the failure is reported via
 * {@code sendError(413)}, so the error dispatch re-enters the {@code DispatcherServlet}, which would try to parse the
 * same oversized body again and fail before the error controller runs, leaving an empty response. The error path never
 * needs the request body, so it is presented as a non-multipart request.
 */
public class MultipartErrorDispatchFilter extends OncePerRequestFilter {

    private static final String MULTIPART_PREFIX = "multipart/";

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getDispatcherType() != DispatcherType.ERROR
                || !StringUtils.startsWithIgnoreCase(request.getContentType(), MULTIPART_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(new NonMultipartRequest(request), response);
    }

    private static final class NonMultipartRequest extends HttpServletRequestWrapper {

        NonMultipartRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public String getHeader(String name) {
            return HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)
                    ? Collections.emptyEnumeration()
                    : super.getHeaders(name);
        }
    }
}
