package com.example.mockretest.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.mockretest.expense.dto.ExpenseRequest;
import com.example.mockretest.expense.dto.ExpenseResponse;
import com.example.mockretest.expense.dto.ExpenseSummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository repository;

    @InjectMocks
    private ExpenseService service;

    @Test
    void createNormalizesCategoryAndReturnsAmountWithScaleTwo() {
        given(repository.save(any(Expense.class))).willAnswer(inv -> inv.getArgument(0));

        ExpenseResponse response =
                service.create(new ExpenseRequest(new BigDecimal("12.5"), "travel", LocalDate.of(2026, 3, 1), null));

        assertThat(response.category()).isEqualTo(ExpenseCategory.TRAVEL);
        assertThat(response.amount()).isEqualByComparingTo("12.5");
        assertThat(response.amount().scale()).isEqualTo(2);
        assertThat(response.amount().toPlainString()).isEqualTo("12.50");
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        given(repository.findById(5L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(5L)).isInstanceOf(ExpenseNotFoundException.class);
    }

    @Test
    void updateThrowsNotFoundForUnknownId() {
        given(repository.findById(5L)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                        service.update(5L, new ExpenseRequest(BigDecimal.ONE, "FOOD", LocalDate.of(2026, 3, 1), null)))
                .isInstanceOf(ExpenseNotFoundException.class);
    }

    @Test
    void deleteThrowsNotFoundForUnknownId() {
        given(repository.existsById(5L)).willReturn(false);

        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(ExpenseNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void listRejectsFromAfterTo() {
        assertThatThrownBy(() -> service.list(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 1), null))
                .isInstanceOf(InvalidExpenseQueryException.class);
    }

    @Test
    void listRejectsUnknownCategory() {
        assertThatThrownBy(() -> service.list(null, null, "CLOTHES")).isInstanceOf(InvalidExpenseQueryException.class);
    }

    @Test
    void listAcceptsCaseInsensitiveCategory() {
        given(repository.search(null, null, ExpenseCategory.BILLS)).willReturn(List.of());

        assertThat(service.list(null, null, "bills")).isEmpty();
    }

    @Test
    void summaryQueriesWholeMonthIncludingLeapDay() {
        given(repository.sumByCategoryBetween(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)))
                .willReturn(List.of());

        service.summary(YearMonth.of(2028, 2));

        verify(repository).sumByCategoryBetween(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29));
    }

    @Test
    void summaryListsEveryCategoryWithZeroDefaultsAndExactOverall() {
        given(repository.sumByCategoryBetween(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .willReturn(List.of(
                        new ExpenseCategoryTotal(ExpenseCategory.FOOD, new BigDecimal("0.30")),
                        new ExpenseCategoryTotal(ExpenseCategory.BILLS, new BigDecimal("100.1"))));

        ExpenseSummaryResponse summary = service.summary(YearMonth.of(2026, 3));

        assertThat(summary.month()).isEqualTo("2026-03");
        assertThat(summary.totals()).containsOnlyKeys("FOOD", "TRAVEL", "BILLS", "OTHER");
        assertThat(summary.totals().get("FOOD").toPlainString()).isEqualTo("0.30");
        assertThat(summary.totals().get("TRAVEL").toPlainString()).isEqualTo("0.00");
        assertThat(summary.totals().get("BILLS").toPlainString()).isEqualTo("100.10");
        assertThat(summary.totals().get("OTHER").toPlainString()).isEqualTo("0.00");
        assertThat(summary.overall().toPlainString()).isEqualTo("100.40");
    }
}
