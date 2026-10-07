package com.example.mockretest.expense;

import com.example.mockretest.expense.dto.ExpenseRequest;
import com.example.mockretest.expense.dto.ExpenseResponse;
import com.example.mockretest.expense.dto.ExpenseSummaryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExpenseService {

    private final ExpenseRepository repository;

    public ExpenseService(ExpenseRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ExpenseResponse create(ExpenseRequest request) {
        Expense expense = new Expense(request.amount(), toCategory(request.category()), request.date(), request.note());
        return ExpenseResponse.from(repository.save(expense));
    }

    public ExpenseResponse get(Long id) {
        return ExpenseResponse.from(find(id));
    }

    public List<ExpenseResponse> list(LocalDate from, LocalDate to, String category) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidExpenseQueryException("'from' must not be after 'to'");
        }
        ExpenseCategory categoryFilter = category == null || category.isBlank() ? null : toCategory(category);
        return repository.search(from, to, categoryFilter).stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    @Transactional
    public ExpenseResponse update(Long id, ExpenseRequest request) {
        Expense expense = find(id);
        expense.apply(request.amount(), toCategory(request.category()), request.date(), request.note());
        return ExpenseResponse.from(repository.save(expense));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ExpenseNotFoundException(id);
        }
        repository.deleteById(id);
    }

    public ExpenseSummaryResponse summary(YearMonth month) {
        Map<ExpenseCategory, BigDecimal> sums = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategoryTotal row : repository.sumByCategoryBetween(month.atDay(1), month.atEndOfMonth())) {
            sums.put(row.category(), row.total());
        }
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal overall = BigDecimal.ZERO;
        for (ExpenseCategory category : ExpenseCategory.values()) {
            BigDecimal total = sums.getOrDefault(category, BigDecimal.ZERO);
            totals.put(category.name(), money(total));
            overall = overall.add(total);
        }
        return new ExpenseSummaryResponse(month.toString(), totals, money(overall));
    }

    private Expense find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ExpenseNotFoundException(id));
    }

    private static ExpenseCategory toCategory(String value) {
        return ExpenseCategory.parse(value)
                .orElseThrow(
                        () -> new InvalidExpenseQueryException("category must be one of FOOD, TRAVEL, BILLS, OTHER"));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(ExpenseResponse.MONEY_SCALE, RoundingMode.UNNECESSARY);
    }
}
