package com.example.mockretest.files;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileContentTypeDetectorTest {

    private final FileContentTypeDetector detector = new FileContentTypeDetector();

    @Test
    void detectsPng() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        assertThat(detector.detect(png)).contains("image/png");
    }

    @Test
    void detectsJpeg() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        assertThat(detector.detect(jpeg)).contains("image/jpeg");
    }

    @Test
    void detectsPdf() {
        assertThat(detector.detect("%PDF-1.7\n".getBytes())).contains("application/pdf");
    }

    @Test
    void rejectsWindowsExecutable() {
        assertThat(detector.detect(new byte[] {'M', 'Z', (byte) 0x90, 0x00})).isEmpty();
    }

    @Test
    void rejectsTruncatedSignatures() {
        assertThat(detector.detect(new byte[] {(byte) 0x89, 0x50})).isEmpty();
        assertThat(detector.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(detector.detect(new byte[0])).isEmpty();
    }
}
