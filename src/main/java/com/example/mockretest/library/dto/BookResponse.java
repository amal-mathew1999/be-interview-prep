package com.example.mockretest.library.dto;

public record BookResponse(
        long id, String title, String author, String isbn, Integer publishedYear, boolean available) {}
