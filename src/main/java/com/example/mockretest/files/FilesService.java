package com.example.mockretest.files;

import java.io.IOException;
import java.io.InputStream;
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
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FilesService {

    private static final Logger LOG = LoggerFactory.getLogger(FilesService.class);
    private static final String FALLBACK_NAME = "file";
    private static final int MAX_NAME_LENGTH = 255;

    private final StoredFileRepository repository;
    private final FileContentTypeDetector detector;
    private final Path storageDir;
    private final DataSize maxSize;

    public FilesService(StoredFileRepository repository, FileContentTypeDetector detector, FilesProperties properties) {
        this.repository = repository;
        this.detector = detector;
        this.storageDir = properties.storageDir().toAbsolutePath().normalize();
        this.maxSize = properties.maxSize();
    }

    @Transactional
    public StoredFile upload(MultipartFile multipartFile) {
        if (multipartFile.isEmpty()) {
            throw new EmptyFileException();
        }
        if (multipartFile.getSize() > maxSize.toBytes()) {
            throw new FileTooLargeException(describe(maxSize));
        }
        String contentType = detector.detect(readHeader(multipartFile)).orElseThrow(UnsupportedFileTypeException::new);

        UUID id = UUID.randomUUID();
        Path target = resolveInsideStorage(id);
        try {
            Files.createDirectories(storageDir);
            try (InputStream in = multipartFile.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            throw new FileStorageException("Could not store file", e);
        }

        StoredFile record = new StoredFile(
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
    public List<StoredFile> list() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "uploadedAt"));
    }

    @Transactional(readOnly = true)
    public FileDownload download(String id) {
        StoredFile file = find(id);
        Path path = resolveInsideStorage(file.getId());
        if (!Files.isRegularFile(path)) {
            LOG.warn("Bytes missing on disk for stored file {}", file.getId());
            throw new StoredFileNotFoundException(id);
        }
        return new FileDownload(file, new FileSystemResource(path));
    }

    @Transactional
    public void delete(String id) {
        StoredFile file = find(id);
        repository.delete(file);
        repository.flush();
        try {
            Files.deleteIfExists(resolveInsideStorage(file.getId()));
        } catch (IOException e) {
            throw new FileStorageException("Could not delete file", e);
        }
    }

    private StoredFile find(String id) {
        return parseId(id).flatMap(repository::findById).orElseThrow(() -> new StoredFileNotFoundException(id));
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
            return in.readNBytes(FileContentTypeDetector.SIGNATURE_LENGTH);
        } catch (IOException e) {
            throw new FileStorageException("Could not read uploaded file", e);
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
        String cleaned = name.replaceAll("\\p{Cntrl}", "");
        int lastSeparator = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        String segment = cleaned.substring(lastSeparator + 1).strip();
        if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
            return FALLBACK_NAME;
        }
        return segment.length() > MAX_NAME_LENGTH ? segment.substring(0, MAX_NAME_LENGTH) : segment;
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
