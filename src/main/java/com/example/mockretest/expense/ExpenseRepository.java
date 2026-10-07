package com.example.mockretest.expense;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Query(
            """
            select e from Expense e
            where (:from is null or e.expenseDate >= :from)
              and (:to is null or e.expenseDate <= :to)
              and (:category is null or e.category = :category)
            order by e.expenseDate, e.id
            """)
    List<Expense> search(
            @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("category") ExpenseCategory category);

    @Query(
            """
            select new com.example.mockretest.expense.ExpenseCategoryTotal(e.category, sum(e.amount))
            from Expense e
            where e.expenseDate >= :start and e.expenseDate <= :end
            group by e.category
            """)
    List<ExpenseCategoryTotal> sumByCategoryBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
