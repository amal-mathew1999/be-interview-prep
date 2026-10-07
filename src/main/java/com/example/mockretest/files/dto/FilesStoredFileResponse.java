package com.example.mockretest.files.dto;

import com.example.mockretest.files.FilesStoredFile;
import java.time.Instant;
import java.util.UUID;

public record FilesStoredFileResponse(UUID id, String originalName, String contentType, long size, Instant uploadedAt) {

    public static FilesStoredFileResponse from(FilesStoredFile file) {
        return new FilesStoredFileResponse(
                file.getId(), file.getOriginalName(), file.getContentType(), file.getSize(), file.getUploadedAt());
    }
}
