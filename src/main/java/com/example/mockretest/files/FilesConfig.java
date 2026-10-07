package com.example.mockretest.files;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.util.unit.DataSize;

@Configuration
@PropertySource("classpath:files.properties")
@EnableConfigurationProperties(FilesProperties.class)
public class FilesConfig {

    static final String UPLOAD_PATH = "/api/files";

    static final String MAX_FILE_SIZE_KEY = "spring.servlet.multipart.max-file-size";
    static final String MAX_REQUEST_SIZE_KEY = "spring.servlet.multipart.max-request-size";
    // Spring Boot's defaults for the keys above when they are not set.
    private static final DataSize DEFAULT_MAX_FILE_SIZE = DataSize.ofMegabytes(1);
    private static final DataSize DEFAULT_MAX_REQUEST_SIZE = DataSize.ofMegabytes(10);

    @Bean
    FilterRegistrationBean<FilesUploadResponseFilter> filesUploadResponseFilter() {
        FilterRegistrationBean<FilesUploadResponseFilter> registration =
                new FilterRegistrationBean<>(new FilesUploadResponseFilter());
        registration.addUrlPatterns(UPLOAD_PATH);
        return registration;
    }

    /**
     * Fails startup when the app-level container multipart limits are below {@code files.max-size}: the container
     * would then reject allowed uploads before the files feature's own check runs.
     */
    @Bean
    FilesMultipartLimits filesMultipartLimits(FilesProperties properties, Environment environment) {
        Binder binder = Binder.get(environment);
        FilesMultipartLimits limits = new FilesMultipartLimits(
                binder.bind(MAX_FILE_SIZE_KEY, DataSize.class).orElse(DEFAULT_MAX_FILE_SIZE),
                binder.bind(MAX_REQUEST_SIZE_KEY, DataSize.class).orElse(DEFAULT_MAX_REQUEST_SIZE));
        limits.verifyAllows(properties.maxSize());
        return limits;
    }

    /** Container multipart limits; a negative size means unlimited. */
    record FilesMultipartLimits(DataSize maxFileSize, DataSize maxRequestSize) {

        void verifyAllows(DataSize maxSize) {
            if (!isUnlimited(maxFileSize) && maxFileSize.toBytes() < maxSize.toBytes()) {
                throw new IllegalStateException(
                        MAX_FILE_SIZE_KEY + " (" + maxFileSize + ") must be at least files.max-size (" + maxSize + ")");
            }
            // The request also carries multipart headers and boundaries, so it must be strictly larger.
            if (!isUnlimited(maxRequestSize) && maxRequestSize.toBytes() <= maxSize.toBytes()) {
                throw new IllegalStateException(MAX_REQUEST_SIZE_KEY + " (" + maxRequestSize
                        + ") must be greater than files.max-size (" + maxSize + ")");
            }
        }

        private static boolean isUnlimited(DataSize size) {
            return size.isNegative();
        }
    }
}
