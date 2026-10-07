package com.example.mockretest.ratelimit;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stands in for another feature's controller (outside {@code /api/quotes}) in {@link RateLimitSliceIsolationTest}.
 * Active only under the {@value #PROFILE} profile so it never leaks into other test contexts.
 */
@RestController
@Profile(RateLimitSliceProbeController.PROFILE)
class RateLimitSliceProbeController {

    static final String PROFILE = "ratelimit-slice-probe";
    static final String PATH = "/ratelimit-slice-probe";

    @GetMapping(PATH)
    String probe() {
        return "ok";
    }
}
