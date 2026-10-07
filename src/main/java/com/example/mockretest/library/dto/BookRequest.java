package com.example.mockretest.library.dto;

import com.example.mockretest.library.Book;
import com.example.mockretest.library.PublishedYearNotInFuture;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BookRequest(
        @NotBlank @Size(max = Book.TITLE_MAX_LENGTH) String title,
        @NotBlank @Size(max = Book.AUTHOR_MAX_LENGTH) String author,
        @NotBlank @Size(max = Book.ISBN_MAX_LENGTH) String isbn,
        @PublishedYearNotInFuture Integer publishedYear) {}
