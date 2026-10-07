package com.example.mockretest.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
    void rejectsZeroWindowAtStartup() {
        runner.withPropertyValues("ratelimit.window=0s")
                .run(context -> assertThat(context).hasFailed().getFailure().hasStackTraceContaining("window"));
    }

    @Test
    void rejectsNegativeWindowAtStartup() {
        runner.withPropertyValues("ratelimit.window=-5s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveLimitAtStartup() {
        runner.withPropertyValues("ratelimit.limit=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class PropertiesOnlyConfig {}
}
