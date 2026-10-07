package com.example.mockretest.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Simulates another feature in the merged app that publishes its own {@link Clock} and injects {@code Clock} by type:
 * the rate-limit feature must not contribute a second {@code Clock} bean that would make that injection ambiguous.
 */
@SpringBootTest
class RateLimitClockIsolationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private OtherFeatureClockConsumer otherFeatureClockConsumer;

    @Test
    void doesNotExposeRawClockBean() {
        assertThat(context.getBeanNamesForType(Clock.class)).containsExactly("otherFeatureClock");
        assertThat(context.getBeanNamesForType(RateLimitClock.class)).containsExactly("rateLimitClock");
    }

    @Test
    void otherFeatureCanInjectClockByType() {
        assertThat(otherFeatureClockConsumer.clock()).isSameAs(context.getBean("otherFeatureClock"));
    }

    record OtherFeatureClockConsumer(Clock clock) {}

    @TestConfiguration(proxyBeanMethods = false)
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
