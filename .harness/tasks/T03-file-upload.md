---
id: T03
title: File upload service with content validation
branch: feature/q3-file-upload
depends_on: []
owns:
  - src/main/java/com/example/mockretest/files/**
  - src/test/java/com/example/mockretest/files/**
  - src/main/resources/files.properties
---

## Goal
Service to upload, list, download and delete files safely.

## Acceptance criteria
- AC1: `POST /api/files` (multipart, part name `file`) → `201` + `Location` + record JSON (`id`, `originalName`, `contentType`, `size`, `uploadedAt`).
- AC2: Only JPEG, PNG and PDF are accepted. The type is determined from the file's actual content (magic bytes), not from the file name or the client-sent `Content-Type`. A non-image renamed `photo.png` (e.g. bytes of a Windows executable `MZ...`) → `415` with a clear `detail`. Empty file → `400`.
- AC3: Files larger than 5 MB → `413` with a clear `detail`; exactly 5 MB is accepted. The limit is configurable without code changes.
- AC4: `GET /api/files` lists all records with `originalName`, `contentType`, `size`, `uploadedAt`.
- AC5: `GET /api/files/{id}` downloads the bytes with the detected `Content-Type` and `Content-Disposition: attachment` carrying the original file name (safely encoded, including non-ASCII and quotes). Unknown id → `404`.
- AC6: `DELETE /api/files/{id}` → `204`; removes both the stored bytes and the record; unknown id → `404`.
- AC7: Path traversal is impossible: stored files are named by server-generated identifiers, never by the user-supplied name; names like `../../etc/passwd`, `..\\..\\win.ini`, absolute paths, or names with NUL characters cannot read or write outside the storage directory. The original name is kept only as metadata (sanitized to its last path segment).
- AC8: Storage directory is configurable without code changes.
- AC9: All errors use `application/problem+json` (`type`, `title`, `status`, `detail`, `instance`).
- AC10: Automated tests cover: valid PNG/JPEG/PDF upload, renamed executable rejected, oversized rejected, traversal filename stored safely inside storage dir, download name header, delete removes file from disk.

## Out of scope
- Authentication, virus scanning, any other feature package, `application.properties`, `pom.xml`.

## Implementation notes
- Package `com.example.mockretest.files`; entity `StoredFile` (table `stored_file`).
- Magic bytes: JPEG `FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`, PDF `25 50 44 46 2D` (`%PDF-`).
- `files.properties`: `files.storage-dir=${java.io.tmpdir}/mockretest-files`, `files.max-size=5MB`, and `spring.servlet.multipart.max-file-size=6MB` / `max-request-size=7MB` so the app-level check (with clear 413) runs; also handle `MaxUploadSizeExceededException` → 413.
- Resolve target with `storageDir.resolve(uuid).normalize()` and assert `startsWith(storageDir)`.
- Use `ContentDisposition.attachment().filename(name, UTF_8)`.
- Tests: `@TempDir` + `@DynamicPropertySource` for storage dir.
