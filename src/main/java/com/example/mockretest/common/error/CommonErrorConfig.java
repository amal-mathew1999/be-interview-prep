package com.example.mockretest.common.error;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Wiring for the shared problem+json error fallback. */
@Configuration(proxyBeanMethods = false)
public class CommonErrorConfig {

    @Bean
    FilterRegistrationBean<MultipartErrorDispatchFilter> multipartErrorDispatchFilter() {
        FilterRegistrationBean<MultipartErrorDispatchFilter> registration =
                new FilterRegistrationBean<>(new MultipartErrorDispatchFilter());
        registration.setDispatcherTypes(DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
