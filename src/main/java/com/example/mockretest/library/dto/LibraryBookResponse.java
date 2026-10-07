package com.example.mockretest.library.dto;

public record LibraryBookResponse(
        long id, String title, String author, String isbn, Integer publishedYear, boolean available) {}
