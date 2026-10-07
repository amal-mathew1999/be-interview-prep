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
 * RATELIMIT_LIMIT} and {@code RATELIMIT_WINDOW}).
 *
 * @param limit maximum requests allowed per key within one window
 * @param window length of the fixed window; must be positive
 */
@Validated
@ConfigurationProperties(prefix = "ratelimit")
public record RateLimitProperties(
        @DefaultValue("10") @Positive int limit,
        @DefaultValue("1m") @NotNull @DurationMin(nanos = 1) Duration window) {}
