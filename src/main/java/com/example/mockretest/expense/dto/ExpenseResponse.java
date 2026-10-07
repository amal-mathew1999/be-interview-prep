package com.example.mockretest.expense.dto;

import com.example.mockretest.expense.Expense;
import com.example.mockretest.expense.ExpenseCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public record ExpenseResponse(Long id, BigDecimal amount, ExpenseCategory category, LocalDate date, String note) {

    public static final int MONEY_SCALE = 2;

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getAmount().setScale(MONEY_SCALE, RoundingMode.UNNECESSARY),
                expense.getCategory(),
                expense.getExpenseDate(),
                expense.getNote());
    }
}
