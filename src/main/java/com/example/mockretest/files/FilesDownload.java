package com.example.mockretest.files;

import org.springframework.core.io.Resource;

/** A stored file's metadata together with a handle to its bytes. */
public record FilesDownload(FilesStoredFile file, Resource resource) {}
