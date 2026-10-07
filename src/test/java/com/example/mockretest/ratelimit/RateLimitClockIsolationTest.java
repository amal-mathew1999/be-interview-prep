package com.example.mockretest.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Simulates another feature in the merged app that publishes its own {@link Clock} and injects {@code Clock} by type:
 * the rate-limit feature must not contribute a second {@code Clock} bean that would make that injection ambiguous.
 * Runs in an isolated context (this feature's config plus a stand-in feature only), so it neither depends on nor
 * breaks because of other features' beans.
 */
class RateLimitClockIsolationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RateLimitConfig.class, RateLimitService.class, OtherFeatureConfig.class)
            .withBean(JsonMapper.class, JsonMapper::new);

    @Test
    void doesNotExposeRawClockBean() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeanNamesForType(Clock.class)).containsExactly("otherFeatureClock");
            assertThat(context.getBeanNamesForType(RateLimitClock.class)).containsExactly("rateLimitClock");
        });
    }

    @Test
    void otherFeatureCanInjectClockByType() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(OtherFeatureClockConsumer.class).clock())
                    .isSameAs(context.getBean("otherFeatureClock"));
        });
    }

    record OtherFeatureClockConsumer(Clock clock) {}

    @Configuration(proxyBeanMethods = false)
    static class OtherFeatureConfig {

        @Bean
        Clock otherFeatureClock() {
            return Clock.systemUTC();
        }

        @Bean
        OtherFeatureClockConsumer otherFeatureClockConsumer(Clock clock) {
            return new OtherFeatureClockConsumer(clock);
        }
    }
}
