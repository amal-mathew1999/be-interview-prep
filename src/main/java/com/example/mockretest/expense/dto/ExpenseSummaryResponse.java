package com.example.mockretest.expense.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ExpenseSummaryResponse(String month, Map<String, BigDecimal> totals, BigDecimal overall) {}
