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
    FilterRegistrationBean<CommonMultipartErrorDispatchFilter> commonMultipartErrorDispatchFilter() {
        FilterRegistrationBean<CommonMultipartErrorDispatchFilter> registration =
                new FilterRegistrationBean<>(new CommonMultipartErrorDispatchFilter());
        registration.setDispatcherTypes(DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    FilterRegistrationBean<CommonErrorResponseBufferingFilter> commonErrorResponseBufferingFilter() {
        FilterRegistrationBean<CommonErrorResponseBufferingFilter> registration =
                new FilterRegistrationBean<>(new CommonErrorResponseBufferingFilter());
        registration.setDispatcherTypes(DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
