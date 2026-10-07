package com.example.mockretest.files;

import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Detects the allowed file types from their leading magic bytes, ignoring names and client headers. */
@Component
public class FileContentTypeDetector {

    /** Number of leading bytes needed to recognise every supported signature. */
    public static final int SIGNATURE_LENGTH = 8;

    private record Signature(String contentType, byte[] magic) {}

    private static final List<Signature> SIGNATURES = List.of(
            new Signature(MediaType.IMAGE_JPEG_VALUE, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
            new Signature(
                    MediaType.IMAGE_PNG_VALUE, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
            new Signature(MediaType.APPLICATION_PDF_VALUE, new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D}));

    public Optional<String> detect(byte[] header) {
        for (Signature signature : SIGNATURES) {
            if (startsWith(header, signature.magic())) {
                return Optional.of(signature.contentType());
            }
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
