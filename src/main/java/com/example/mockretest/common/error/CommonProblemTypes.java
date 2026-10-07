package com.example.mockretest.common.error;

import java.net.URI;

/** Problem type URIs used by the shared error fallback. Always explicit so {@code type} is never omitted. */
public final class CommonProblemTypes {

    private static final String PREFIX = "urn:problem-type:common:";

    private CommonProblemTypes() {}

    public static URI forStatus(int status) {
        return URI.create(PREFIX + status);
    }
}
