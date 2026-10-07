package com.example.mockretest.expense;

public class ExpenseNotFoundException extends RuntimeException {

    public ExpenseNotFoundException(Long id) {
        super("Expense " + id + " not found");
    }
}
