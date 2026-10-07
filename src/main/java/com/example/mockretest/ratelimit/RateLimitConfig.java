package com.example.mockretest.ratelimit;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires per-API-key rate limiting onto the protected {@code /api/quotes/**} endpoints. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RateLimitProperties.class)
@PropertySource("classpath:ratelimit.properties")
public class RateLimitConfig implements WebMvcConfigurer {

    static final String PROTECTED_PATHS = "/api/quotes/**";

    private final RateLimitInterceptor rateLimitInterceptor;

    public RateLimitConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    /** Clock used for rate-limit windows; static so it can be created before this configuration is instantiated. */
    @Bean
    static Clock rateLimitClock() {
        return Clock.systemUTC();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns(PROTECTED_PATHS);
    }
}
