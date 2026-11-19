package com.fintrack.budget.exception;

import com.fintrack.common.exception.ConflictException;

public class BudgetAlreadyExistsException extends ConflictException {

    public BudgetAlreadyExistsException() {
        super("A budget for this category, month and currency already exists. Update it instead.");
    }
}
