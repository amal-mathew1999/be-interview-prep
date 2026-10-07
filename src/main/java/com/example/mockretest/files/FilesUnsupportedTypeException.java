package com.example.mockretest.files;

public class FilesUnsupportedTypeException extends RuntimeException {

    public FilesUnsupportedTypeException() {
        super("Unsupported file type: only JPEG, PNG, PDF files are accepted"
                + " (the type is detected from the file content, not its name)");
    }
}
