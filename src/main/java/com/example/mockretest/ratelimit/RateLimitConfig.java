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
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires per-API-key rate limiting onto the protected {@code /api/quotes/**} endpoints.
 *
 * <p>This class deliberately does not implement {@link WebMvcConfigurer} and the interceptor is not a component:
 * {@code @WebMvcTest} slices include every scanned {@code WebMvcConfigurer} / {@code HandlerInterceptor} but exclude
 * {@link RateLimitService}, so either would break other features' slice tests. A plain {@code @Configuration} is
 * excluded from slices, and with it the {@code WebMvcConfigurer} bean it declares.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RateLimitProperties.class)
@PropertySource("classpath:ratelimit.properties")
public class RateLimitConfig implements SchedulingConfigurer {

    static final String PROTECTED_PATHS = "/api/quotes/**";

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;

    public RateLimitConfig(RateLimitService rateLimitService, RateLimitProperties properties) {
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

    /** Registers the rate-limit interceptor for {@value #PROTECTED_PATHS}. */
    @Bean
    WebMvcConfigurer rateLimitWebMvcConfigurer(JsonMapper jsonMapper) {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(rateLimitService, properties, jsonMapper);
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns(PROTECTED_PATHS);
            }
        };
    }

    /** Schedules eviction of expired windows at the validated {@code ratelimit.eviction-interval}. */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(rateLimitService::evictExpired, properties.evictionInterval());
    }
}
