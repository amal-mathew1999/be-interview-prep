package com.example.mockretest.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RateLimitPropertiesValidationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesOnlyConfig.class);

    @Test
    void bindsPositiveWindow() {
        runner.withPropertyValues("ratelimit.window=30s").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RateLimitProperties.class).window()).isEqualTo(Duration.ofSeconds(30));
        });
    }

    @Test
    void bindsDefaultsForEvictionIntervalAndMaxKeyLength() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            RateLimitProperties properties = context.getBean(RateLimitProperties.class);
            assertThat(properties.evictionInterval()).isEqualTo(Duration.ofMinutes(1));
            assertThat(properties.maxApiKeyLength()).isEqualTo(128);
        });
    }

    @Test
    void rejectsZeroWindowAtStartup() {
        runner.withPropertyValues("ratelimit.window=0s").run(context -> assertBindValidationFailure(context, "window"));
    }

    @Test
    void rejectsNegativeWindowAtStartup() {
        runner.withPropertyValues("ratelimit.window=-5s")
                .run(context -> assertBindValidationFailure(context, "window"));
    }

    @Test
    void rejectsNonPositiveLimitAtStartup() {
        runner.withPropertyValues("ratelimit.limit=0").run(context -> assertBindValidationFailure(context, "limit"));
    }

    @Test
    void rejectsZeroEvictionIntervalAtStartup() {
        runner.withPropertyValues("ratelimit.eviction-interval=0s")
                .run(context -> assertBindValidationFailure(context, "evictionInterval"));
    }

    @Test
    void rejectsNegativeEvictionIntervalAtStartup() {
        runner.withPropertyValues("ratelimit.eviction-interval=-1m")
                .run(context -> assertBindValidationFailure(context, "evictionInterval"));
    }

    @Test
    void rejectsNonPositiveMaxApiKeyLengthAtStartup() {
        runner.withPropertyValues("ratelimit.max-api-key-length=0")
                .run(context -> assertBindValidationFailure(context, "maxApiKeyLength"));
    }

    private static void assertBindValidationFailure(AssertableApplicationContext context, String field) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure())
                .rootCause()
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("on field '" + field + "'");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class PropertiesOnlyConfig {}
}
