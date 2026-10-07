package com.example.mockretest.expense;

public class InvalidExpenseQueryException extends RuntimeException {

    public InvalidExpenseQueryException(String message) {
        super(message);
    }
}
