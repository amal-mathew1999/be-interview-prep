package com.example.mockretest.files;

public class FilesEmptyFileException extends RuntimeException {

    public FilesEmptyFileException() {
        super("The uploaded file is empty");
    }
}
