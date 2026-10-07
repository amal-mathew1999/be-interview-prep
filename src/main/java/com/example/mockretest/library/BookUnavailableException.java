package com.example.mockretest.library;

public class BookUnavailableException extends RuntimeException {

    public BookUnavailableException(long bookId) {
        super("Book %d is currently borrowed.".formatted(bookId));
    }
}
