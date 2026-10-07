package com.example.mockretest.files;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilesStoredFileRepository extends JpaRepository<FilesStoredFile, UUID> {}
