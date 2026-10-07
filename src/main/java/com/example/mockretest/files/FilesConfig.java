package com.example.mockretest.files;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:files.properties")
@EnableConfigurationProperties(FilesProperties.class)
public class FilesConfig {}
