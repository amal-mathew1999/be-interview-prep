package com.example.mockretest.library;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration(proxyBeanMethods = false)
@PropertySource("classpath:library.properties")
@EnableConfigurationProperties(LibraryProperties.class)
public class LibraryConfig {

    @Bean
    Clock libraryClock(LibraryProperties properties) {
        return Clock.system(properties.zoneId());
    }
}
