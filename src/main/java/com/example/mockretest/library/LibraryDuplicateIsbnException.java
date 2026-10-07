package com.example.mockretest.library;

public class LibraryDuplicateIsbnException extends RuntimeException {

    public LibraryDuplicateIsbnException(String isbn) {
        super("A book with ISBN '%s' already exists.".formatted(isbn));
    }
}
