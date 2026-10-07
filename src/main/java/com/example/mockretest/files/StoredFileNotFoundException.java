package com.example.mockretest.files;

public class StoredFileNotFoundException extends RuntimeException {

    public StoredFileNotFoundException(String id) {
        super("No file found with id " + id);
    }
}
