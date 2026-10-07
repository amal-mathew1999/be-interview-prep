package com.example.mockretest.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FilesService {

    private static final Logger LOG = LoggerFactory.getLogger(FilesService.class);
    private static final String FALLBACK_NAME = "file";
    private static final int MAX_NAME_LENGTH = 255;

    private final FilesStoredFileRepository repository;
    private final FilesContentTypeDetector detector;
    private final Path storageDir;
    private final DataSize maxSize;

    public FilesService(
            FilesStoredFileRepository repository, FilesContentTypeDetector detector, FilesProperties properties) {
        this.repository = repository;
        this.detector = detector;
        this.storageDir = properties.storageDir().toAbsolutePath().normalize();
        this.maxSize = properties.maxSize();
    }

    /**
     * Not {@code @Transactional}: the bytes are copied to disk without holding a database connection, then the record
     * is persisted in the repository's own short transaction. If that save (including its commit) fails, the bytes are
     * removed so no orphan file is left behind.
     */
    public FilesStoredFile upload(MultipartFile multipartFile) {
        if (multipartFile.isEmpty()) {
            throw new FilesEmptyFileException();
        }
        if (multipartFile.getSize() > maxSize.toBytes()) {
            throw new FilesTooLargeException(describe(maxSize));
        }
        String contentType = detector.detect(readHeader(multipartFile)).orElseThrow(FilesUnsupportedTypeException::new);

        UUID id = UUID.randomUUID();
        Path target = resolveInsideStorage(id);
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new FilesStorageException("Could not create storage directory", e);
        }
        try (InputStream in = multipartFile.getInputStream()) {
            Files.copy(in, target);
        } catch (FileAlreadyExistsException e) {
            // Never delete here: the existing file belongs to another record.
            throw new FilesStorageException("Could not store file", e);
        } catch (IOException e) {
            deleteQuietly(target);
            throw new FilesStorageException("Could not store file", e);
        }

        FilesStoredFile record = new FilesStoredFile(
                id,
                sanitizeOriginalName(multipartFile.getOriginalFilename()),
                contentType,
                multipartFile.getSize(),
                Instant.now());
        try {
            return repository.saveAndFlush(record);
        } catch (RuntimeException e) {
            deleteQuietly(target);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<FilesStoredFile> list() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "uploadedAt"));
    }

    @Transactional(readOnly = true)
    public FilesDownload download(String id) {
        FilesStoredFile file = find(id);
        Path path = resolveInsideStorage(file.getId());
        if (!Files.isRegularFile(path)) {
            LOG.warn("Bytes missing on disk for stored file {}", file.getId());
            throw new FilesNotFoundException(id);
        }
        return new FilesDownload(file, new FileSystemResource(path));
    }

    @Transactional
    public void delete(String id) {
        FilesStoredFile file = find(id);
        repository.delete(file);
        repository.flush();
        Path path = resolveInsideStorage(file.getId());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Remove the bytes only once the record deletion is committed; a rollback keeps record and bytes together.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteQuietly(path);
                }
            });
        } else {
            deleteQuietly(path);
        }
    }

    private FilesStoredFile find(String id) {
        return parseId(id).flatMap(repository::findById).orElseThrow(() -> new FilesNotFoundException(id));
    }

    private static Optional<UUID> parseId(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Path resolveInsideStorage(UUID id) {
        Path target = storageDir.resolve(id.toString()).normalize();
        if (!target.startsWith(storageDir) || target.equals(storageDir)) {
            throw new IllegalStateException("Resolved path escapes the storage directory");
        }
        return target;
    }

    private static byte[] readHeader(MultipartFile multipartFile) {
        try (InputStream in = multipartFile.getInputStream()) {
            return in.readNBytes(FilesContentTypeDetector.SIGNATURE_LENGTH);
        } catch (IOException e) {
            throw new FilesStorageException("Could not read uploaded file", e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warn("Could not clean up stored file {}", path.getFileName(), e);
        }
    }

    /** Keeps only the last path segment of a client-supplied name, without control characters. */
    static String sanitizeOriginalName(String name) {
        if (name == null) {
            return FALLBACK_NAME;
        }
        // Cc: control characters (incl. NUL); Cf: format characters such as U+202E RIGHT-TO-LEFT OVERRIDE, which can
        // disguise the real extension when the name is displayed.
        String cleaned = name.replaceAll("[\\p{Cc}\\p{Cf}]", "");
        int lastSeparator = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        String segment = cleaned.substring(lastSeparator + 1).strip();
        if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
            return FALLBACK_NAME;
        }
        return truncate(segment, MAX_NAME_LENGTH);
    }

    /** Cuts to at most {@code maxChars} UTF-16 units without splitting a surrogate pair. */
    private static String truncate(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        int end = maxChars;
        if (Character.isHighSurrogate(value.charAt(end - 1)) && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end);
    }

    /** Human-readable size such as {@code 5MB}, used in error details. */
    static String describe(DataSize size) {
        long bytes = size.toBytes();
        if (bytes % DataSize.ofMegabytes(1).toBytes() == 0) {
            return size.toMegabytes() + "MB";
        }
        if (bytes % DataSize.ofKilobytes(1).toBytes() == 0) {
            return size.toKilobytes() + "KB";
        }
        return bytes + "B";
    }
}
