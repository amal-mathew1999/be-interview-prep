package com.example.mockretest.library.dto;

import com.example.mockretest.library.Loan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BorrowRequest(@NotBlank @Size(max = Loan.MEMBER_ID_MAX_LENGTH) String memberId) {}
