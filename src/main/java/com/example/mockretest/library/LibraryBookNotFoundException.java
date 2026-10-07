package com.example.mockretest.library;

public class LibraryBookNotFoundException extends RuntimeException {

    public LibraryBookNotFoundException(long bookId) {
        super("Book %d was not found.".formatted(bookId));
    }
}
