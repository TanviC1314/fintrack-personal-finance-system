package com.fintrack.account.exception;

import com.fintrack.common.exception.BusinessRuleException;

public class InsufficientFundsException extends BusinessRuleException {

    public InsufficientFundsException() {
        super("Insufficient funds in the account");
    }
}
