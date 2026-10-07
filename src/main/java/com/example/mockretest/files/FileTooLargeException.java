package com.example.mockretest.files;

public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(String limit) {
        super("The uploaded file exceeds the maximum allowed size of " + limit);
    }
}
