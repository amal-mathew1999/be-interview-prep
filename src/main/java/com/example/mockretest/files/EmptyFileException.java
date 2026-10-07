package com.example.mockretest.files;

public class EmptyFileException extends RuntimeException {

    public EmptyFileException() {
        super("The uploaded file is empty");
    }
}
