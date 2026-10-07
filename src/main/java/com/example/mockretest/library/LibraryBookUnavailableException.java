package com.example.mockretest.library;

public class LibraryBookUnavailableException extends RuntimeException {

    public LibraryBookUnavailableException(long bookId) {
        super("Book %d is currently borrowed.".formatted(bookId));
    }
}
