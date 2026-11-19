package com.fintrack.category.exception;

import com.fintrack.common.exception.ConflictException;

public class CategoryInUseException extends ConflictException {

    public CategoryInUseException() {
        super("This category is used by existing transactions and cannot be deleted. Rename it instead.");
    }
}
