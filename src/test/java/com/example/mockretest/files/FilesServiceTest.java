package com.example.mockretest.files;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FilesServiceTest {

    @Test
    void sanitizeKeepsOnlyLastPathSegment() {
        assertThat(FilesService.sanitizeOriginalName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(FilesService.sanitizeOriginalName("..\\..\\win.ini")).isEqualTo("win.ini");
        assertThat(FilesService.sanitizeOriginalName("/abs/path/photo.png")).isEqualTo("photo.png");
        assertThat(FilesService.sanitizeOriginalName("C:\\Windows\\evil.pdf")).isEqualTo("evil.pdf");
    }

    @Test
    void sanitizeStripsControlCharactersIncludingNul() {
        assertThat(FilesService.sanitizeOriginalName("evil\u0000.png")).isEqualTo("evil.png");
        assertThat(FilesService.sanitizeOriginalName("a\r\nb.png")).isEqualTo("ab.png");
    }

    @Test
    void sanitizeFallsBackForBlankOrDotNames() {
        assertThat(FilesService.sanitizeOriginalName(null)).isEqualTo("file");
        assertThat(FilesService.sanitizeOriginalName("")).isEqualTo("file");
        assertThat(FilesService.sanitizeOriginalName("../..")).isEqualTo("file");
        assertThat(FilesService.sanitizeOriginalName("dir/")).isEqualTo("file");
    }
}
