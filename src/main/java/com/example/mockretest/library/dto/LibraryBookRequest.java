package com.example.mockretest.library.dto;

import com.example.mockretest.library.LibraryBook;
import com.example.mockretest.library.LibraryPublishedYearNotInFuture;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LibraryBookRequest(
        @NotBlank @Size(max = LibraryBook.TITLE_MAX_LENGTH) String title,
        @NotBlank @Size(max = LibraryBook.AUTHOR_MAX_LENGTH) String author,
        @NotBlank @Size(max = LibraryBook.ISBN_MAX_LENGTH) String isbn,
        @LibraryPublishedYearNotInFuture Integer publishedYear) {}
