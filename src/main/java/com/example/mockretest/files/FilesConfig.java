package com.example.mockretest.files;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:files.properties")
@EnableConfigurationProperties(FilesProperties.class)
public class FilesConfig {

    static final String UPLOAD_PATH = "/api/files";

    @Bean
    FilterRegistrationBean<FilesUploadResponseFilter> filesUploadResponseFilter() {
        FilterRegistrationBean<FilesUploadResponseFilter> registration =
                new FilterRegistrationBean<>(new FilesUploadResponseFilter());
        registration.addUrlPatterns(UPLOAD_PATH);
        return registration;
    }
}
