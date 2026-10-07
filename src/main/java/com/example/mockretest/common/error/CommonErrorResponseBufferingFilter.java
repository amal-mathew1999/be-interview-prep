package com.example.mockretest.common.error;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Buffers the body written during the container's error dispatch so the problem response is sent with an explicit
 * {@code Content-Length} and flushed to the client before the container finishes the request.
 *
 * <p>Without buffering, the message converter flushes the error body, committing a chunked response. When the request
 * body was not fully read (e.g. an oversized multipart upload), the container may close the connection while ending the
 * request, before writing the terminating chunk, so clients see a truncated body. A length-delimited response that is
 * already flushed is complete regardless of what happens to the connection afterwards.
 */
public class CommonErrorResponseBufferingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getDispatcherType() != DispatcherType.ERROR;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapper);
        } finally {
            boolean hasBody = wrapper.getContentSize() > 0;
            wrapper.copyBodyToResponse();
            if (hasBody) {
                response.flushBuffer();
            }
        }
    }
}
