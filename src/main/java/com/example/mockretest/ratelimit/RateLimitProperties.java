package com.example.mockretest.ratelimit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Per-API-key rate limit settings, bound from {@code ratelimit.*} (overridable via env vars such as {@code
 * RATELIMIT_LIMIT}, {@code RATELIMIT_WINDOW} and {@code RATELIMIT_EVICTIONINTERVAL}).
 *
 * @param limit maximum requests allowed per key within one window
 * @param window length of the fixed window; must be positive
 * @param evictionInterval delay between sweeps that evict expired windows; must be positive
 * @param maxApiKeyLength longest accepted {@code X-API-Key} value; longer keys are rejected and never tracked
 * @param maxTrackedKeys most API keys tracked at once; when reached, expired windows are evicted inline and, if the
 *     map is still full, requests from not-yet-tracked keys are rejected with {@code 503} until capacity frees up
 */
@Validated
@ConfigurationProperties(prefix = "ratelimit")
public record RateLimitProperties(
        @DefaultValue("10") @Positive int limit,
        @DefaultValue("1m") @NotNull @DurationMin(nanos = 1) Duration window,
        @DefaultValue("1m") @NotNull @DurationMin(nanos = 1) Duration evictionInterval,
        @DefaultValue("128") @Positive int maxApiKeyLength,
        @DefaultValue("100000") @Positive int maxTrackedKeys) {}
