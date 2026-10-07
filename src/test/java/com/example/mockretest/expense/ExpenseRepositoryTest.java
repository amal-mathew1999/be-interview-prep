package com.example.mockretest.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class ExpenseRepositoryTest {

    @Autowired
    private ExpenseRepository repository;

    private void save(String amount, ExpenseCategory category, LocalDate date) {
        repository.saveAndFlush(new Expense(new BigDecimal(amount), category, date, null));
    }

    private Map<ExpenseCategory, BigDecimal> totals(LocalDate start, LocalDate end) {
        return repository.sumByCategoryBetween(start, end).stream()
                .collect(Collectors.toMap(ExpenseCategoryTotal::category, ExpenseCategoryTotal::total));
    }

    @Test
    void sumsDecimalAmountsExactly() {
        save("0.10", ExpenseCategory.FOOD, LocalDate.of(2026, 3, 10));
        save("0.20", ExpenseCategory.FOOD, LocalDate.of(2026, 3, 11));

        BigDecimal food =
                totals(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).get(ExpenseCategory.FOOD);

        assertThat(food).isEqualByComparingTo("0.30");
        assertThat(food.toPlainString()).isEqualTo("0.30");
    }

    @Test
    void storesAmountWithoutFloatingPointLoss() {
        Expense saved = repository.saveAndFlush(
                new Expense(new BigDecimal("9999999999.99"), ExpenseCategory.OTHER, LocalDate.of(2026, 1, 1), null));

        assertThat(repository.findById(saved.getId()).orElseThrow().getAmount()).isEqualByComparingTo("9999999999.99");
    }

    @Test
    void summaryRangeIncludesFirstAndLastDayAndExcludesNeighbours() {
        save("1.00", ExpenseCategory.TRAVEL, LocalDate.of(2026, 2, 28)); // previous month's last day
        save("2.00", ExpenseCategory.TRAVEL, LocalDate.of(2026, 3, 1));
        save("4.00", ExpenseCategory.TRAVEL, LocalDate.of(2026, 3, 31));
        save("8.00", ExpenseCategory.TRAVEL, LocalDate.of(2026, 4, 1)); // next month's first day

        assertThat(totals(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)).get(ExpenseCategory.TRAVEL))
                .isEqualByComparingTo("6.00");
    }

    @Test
    void summaryRangeHandlesLeapFebruary() {
        save("1.00", ExpenseCategory.BILLS, LocalDate.of(2028, 1, 31));
        save("2.00", ExpenseCategory.BILLS, LocalDate.of(2028, 2, 1));
        save("4.00", ExpenseCategory.BILLS, LocalDate.of(2028, 2, 29));
        save("8.00", ExpenseCategory.BILLS, LocalDate.of(2028, 3, 1));

        assertThat(totals(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)).get(ExpenseCategory.BILLS))
                .isEqualByComparingTo("6.00");
    }

    @Test
    void searchAppliesCombinableFilters() {
        save("1.00", ExpenseCategory.FOOD, LocalDate.of(2026, 3, 1));
        save("2.00", ExpenseCategory.TRAVEL, LocalDate.of(2026, 3, 15));
        save("3.00", ExpenseCategory.FOOD, LocalDate.of(2026, 3, 31));
        save("4.00", ExpenseCategory.FOOD, LocalDate.of(2026, 4, 1));

        assertThat(repository.search(null, null, null)).hasSize(4);
        assertThat(amounts(repository.search(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), null)))
                .containsExactly("1.00", "2.00", "3.00");
        assertThat(amounts(repository.search(LocalDate.of(2026, 3, 15), null, ExpenseCategory.FOOD)))
                .containsExactly("3.00", "4.00");
        assertThat(amounts(repository.search(null, LocalDate.of(2026, 3, 15), null)))
                .containsExactly("1.00", "2.00");
        assertThat(amounts(repository.search(null, null, ExpenseCategory.TRAVEL)))
                .containsExactly("2.00");
    }

    private static List<String> amounts(List<Expense> expenses) {
        return expenses.stream()
                .map(e -> e.getAmount().setScale(2).toPlainString())
                .toList();
    }
}
