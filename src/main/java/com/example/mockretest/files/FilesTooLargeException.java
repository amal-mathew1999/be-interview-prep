package com.example.mockretest.files;

public class FilesTooLargeException extends RuntimeException {

    public FilesTooLargeException(String limit) {
        super("The uploaded file exceeds the maximum allowed size of " + limit);
    }
}
