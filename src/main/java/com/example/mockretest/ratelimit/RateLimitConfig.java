package com.example.mockretest.ratelimit;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires per-API-key rate limiting onto the protected {@code /api/quotes/**} endpoints. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RateLimitProperties.class)
@PropertySource("classpath:ratelimit.properties")
public class RateLimitConfig implements WebMvcConfigurer, SchedulingConfigurer {

    static final String PROTECTED_PATHS = "/api/quotes/**";

    private final RateLimitInterceptor rateLimitInterceptor;
    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;

    public RateLimitConfig(
            RateLimitInterceptor rateLimitInterceptor,
            RateLimitService rateLimitService,
            RateLimitProperties properties) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    /**
     * Time source for rate-limit windows; static so it can be created before this configuration is instantiated.
     * Deliberately not a raw {@link Clock} bean so other features can still inject {@code Clock} by type.
     */
    @Bean
    static RateLimitClock rateLimitClock() {
        return new RateLimitClock(Clock.systemUTC());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns(PROTECTED_PATHS);
    }

    /** Schedules eviction of expired windows at the validated {@code ratelimit.eviction-interval}. */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(rateLimitService::evictExpired, properties.evictionInterval());
    }
}
