package com.example.mockretest.library;

public class DuplicateIsbnException extends RuntimeException {

    public DuplicateIsbnException(String isbn) {
        super("A book with ISBN '%s' already exists.".formatted(isbn));
    }
}
