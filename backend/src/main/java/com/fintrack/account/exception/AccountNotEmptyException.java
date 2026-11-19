package com.fintrack.account.exception;

import com.fintrack.common.exception.ConflictException;

public class AccountNotEmptyException extends ConflictException {

    public AccountNotEmptyException() {
        super("An account can only be archived when its balance is zero. Transfer or spend the remaining balance first.");
    }
}
