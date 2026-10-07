package com.example.mockretest.library;

public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(long bookId) {
        super("Book %d was not found.".formatted(bookId));
    }
}
