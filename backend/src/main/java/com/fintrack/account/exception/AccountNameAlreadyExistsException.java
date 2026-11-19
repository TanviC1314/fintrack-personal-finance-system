package com.fintrack.account.exception;

import com.fintrack.common.exception.ConflictException;

public class AccountNameAlreadyExistsException extends ConflictException {

    public AccountNameAlreadyExistsException(String name) {
        super("You already have an active account named '" + name + "'");
    }
}
