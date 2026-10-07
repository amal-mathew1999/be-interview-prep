package com.example.mockretest.files;

public class FilesNotFoundException extends RuntimeException {

    public FilesNotFoundException(String id) {
        super("No file found with id " + id);
    }
}
