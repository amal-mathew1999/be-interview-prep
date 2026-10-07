package com.example.mockretest.expense;

import java.math.BigDecimal;

/** Aggregated sum of expense amounts for one category. */
public record ExpenseCategoryTotal(ExpenseCategory category, BigDecimal total) {}
