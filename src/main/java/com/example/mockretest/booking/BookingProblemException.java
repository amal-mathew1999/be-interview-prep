package com.example.mockretest.booking;

import java.net.URI;
import org.springframework.http.HttpStatus;

/** Base type for booking domain errors rendered as RFC 9457 problem details. */
public abstract class BookingProblemException extends RuntimeException {

    private static final String TYPE_PREFIX = "urn:problem-type:booking:";

    private final HttpStatus status;
    private final String title;
    private final URI type;

    protected BookingProblemException(HttpStatus status, String typeSlug, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
        this.type = URI.create(TYPE_PREFIX + typeSlug);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public URI getType() {
        return type;
    }
}
