package com.example.mockretest.library;

public class LibraryBookNotBorrowedException extends RuntimeException {

    public LibraryBookNotBorrowedException(long bookId) {
        super("Book %d is not currently borrowed.".formatted(bookId));
    }
}
