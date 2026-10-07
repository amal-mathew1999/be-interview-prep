package com.example.mockretest.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

class FilesServiceTest {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @TempDir
    Path storageDir;

    private final FilesStoredFileRepository repository = mock(FilesStoredFileRepository.class);
    private FilesService service;

    @BeforeEach
    void setUp() {
        service = new FilesService(
                repository, new FilesContentTypeDetector(), new FilesProperties(storageDir, DataSize.ofMegabytes(5)));
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

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
    void sanitizeStripsUnicodeFormatCharactersSuchAsRightToLeftOverride() {
        // "invoice‮fdp.exe" displays as "invoiceexe.pdf" but is an .exe.
        assertThat(FilesService.sanitizeOriginalName("invoice‮fdp.exe")).isEqualTo("invoicefdp.exe");
        assertThat(FilesService.sanitizeOriginalName("a​b﻿.png")).isEqualTo("ab.png");
    }

    @Test
    void sanitizeTruncatesLongNamesWithoutSplittingSurrogatePairs() {
        String emoji = "😀"; // U+1F600, two UTF-16 units
        String name = "a".repeat(254) + emoji + ".png";

        String sanitized = FilesService.sanitizeOriginalName(name);

        assertThat(sanitized).isEqualTo("a".repeat(254));
        assertThat(Character.isHighSurrogate(sanitized.charAt(sanitized.length() - 1)))
                .isFalse();
        assertThat(FilesService.sanitizeOriginalName("b".repeat(300))).hasSize(255);
    }

    @Test
    void uploadRemovesStoredBytesWhenPersistingTheRecordFails() throws IOException {
        byte[] bytes = Arrays.copyOf(PNG_MAGIC, 64);
        when(repository.saveAndFlush(any())).thenThrow(new IllegalStateException("commit failed"));

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "photo.png", "image/png", bytes)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(filesIn(storageDir)).isEmpty();
    }

    @Test
    void sanitizeFallsBackForBlankOrDotNames() {
        assertThat(FilesService.sanitizeOriginalName(null)).isEqualTo("file");
        assertThat(FilesService.sanitizeOriginalName("")).isEqualTo("file");
        assertThat(FilesService.sanitizeOriginalName("../..")).isEqualTo("file");
        assertThat(FilesService.sanitizeOriginalName("dir/")).isEqualTo("file");
    }

    @Test
    void uploadRemovesPartiallyWrittenFileWhenCopyFails() throws IOException {
        byte[] bytes = Arrays.copyOf(PNG_MAGIC, 4096);
        InputStream failsMidWrite = new SequenceInputStream(new ByteArrayInputStream(bytes), new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk full");
            }
        });
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(8192L);
        when(file.getOriginalFilename()).thenReturn("photo.png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(bytes), failsMidWrite);

        assertThatThrownBy(() -> service.upload(file)).isInstanceOf(FilesStorageException.class);

        assertThat(filesIn(storageDir)).isEmpty();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void deleteRemovesBytesOnlyAfterCommit() throws IOException {
        UUID id = UUID.randomUUID();
        Path bytes = storedBytes(id);
        TransactionSynchronizationManager.initSynchronization();

        service.delete(id.toString());

        verify(repository).delete(any(FilesStoredFile.class));
        assertThat(bytes)
                .as("bytes must survive until the deletion is committed")
                .exists();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(bytes).doesNotExist();
    }

    @Test
    void deleteKeepsBytesWhenTransactionRollsBack() throws IOException {
        UUID id = UUID.randomUUID();
        Path bytes = storedBytes(id);
        TransactionSynchronizationManager.initSynchronization();

        service.delete(id.toString());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(bytes).exists();
    }

    private Path storedBytes(UUID id) throws IOException {
        Path bytes = Files.write(storageDir.resolve(id.toString()), PNG_MAGIC);
        when(repository.findById(id))
                .thenReturn(Optional.of(
                        new FilesStoredFile(id, "photo.png", "image/png", PNG_MAGIC.length, Instant.now())));
        return bytes;
    }

    private static List<Path> filesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.toList();
        }
    }
}
