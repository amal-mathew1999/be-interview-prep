package com.example.mockretest.files.dto;

import com.example.mockretest.files.StoredFile;
import java.time.Instant;
import java.util.UUID;

public record StoredFileResponse(UUID id, String originalName, String contentType, long size, Instant uploadedAt) {

    public static StoredFileResponse from(StoredFile file) {
        return new StoredFileResponse(
                file.getId(), file.getOriginalName(), file.getContentType(), file.getSize(), file.getUploadedAt());
    }
}
