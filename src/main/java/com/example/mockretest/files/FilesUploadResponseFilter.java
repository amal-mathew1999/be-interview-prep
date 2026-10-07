package com.example.mockretest.files;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Buffers the (small, JSON) response of {@code POST /api/files} so it is sent with a {@code Content-Length} instead of
 * chunked encoding.
 *
 * <p>When an upload is rejected for size, the container stops reading the request body and, once its swallow limit is
 * exceeded, closes the connection before writing the terminating chunk of a chunked response. A length-delimited
 * response is complete as soon as it is written, so clients still receive the full {@code application/problem+json}
 * body.
 */
class FilesUploadResponseFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
        } finally {
            wrapper.copyBodyToResponse();
        }
    }
}
