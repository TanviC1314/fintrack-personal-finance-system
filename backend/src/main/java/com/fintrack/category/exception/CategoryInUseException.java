package com.fintrack.category.exception;

import com.fintrack.common.exception.ConflictException;

public class CategoryInUseException extends ConflictException {

    public CategoryInUseException() {
        super("This category is used by transactions or budgets and cannot be deleted. Rename it instead.");
    }
}
