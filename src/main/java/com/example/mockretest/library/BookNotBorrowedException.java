package com.example.mockretest.library;

public class BookNotBorrowedException extends RuntimeException {

    public BookNotBorrowedException(long bookId) {
        super("Book %d is not currently borrowed.".formatted(bookId));
    }
}
