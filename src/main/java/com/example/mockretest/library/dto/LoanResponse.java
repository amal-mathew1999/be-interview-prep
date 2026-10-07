package com.example.mockretest.library.dto;

import java.time.Instant;

/** A loan; {@code returnedAt} is null while the loan is active. */
public record LoanResponse(long loanId, long bookId, String memberId, Instant borrowedAt, Instant returnedAt) {}
