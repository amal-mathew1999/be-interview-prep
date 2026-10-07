package com.example.mockretest.library.dto;

import com.example.mockretest.library.LibraryLoan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LibraryBorrowRequest(@NotBlank @Size(max = LibraryLoan.MEMBER_ID_MAX_LENGTH) String memberId) {}
