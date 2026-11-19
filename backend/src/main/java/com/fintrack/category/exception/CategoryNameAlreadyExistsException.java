package com.fintrack.category.exception;

import com.fintrack.common.exception.ConflictException;

public class CategoryNameAlreadyExistsException extends ConflictException {

    public CategoryNameAlreadyExistsException(String name) {
        super("You already have a category named '" + name + "'");
    }
}
