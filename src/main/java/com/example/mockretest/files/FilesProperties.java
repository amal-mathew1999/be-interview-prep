package com.example.mockretest.files;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "files")
public record FilesProperties(Path storageDir, DataSize maxSize) {}
