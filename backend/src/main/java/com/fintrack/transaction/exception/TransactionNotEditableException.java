package com.fintrack.transaction.exception;

import com.fintrack.common.exception.ConflictException;

public class TransactionNotEditableException extends ConflictException {

    private TransactionNotEditableException(String message) {
        super(message);
    }

    public static TransactionNotEditableException reversed() {
        return new TransactionNotEditableException("This transaction has been reversed and can no longer be changed");
    }

    public static TransactionNotEditableException transferLeg() {
        return new TransactionNotEditableException(
                "Transfer transactions cannot be changed individually; they are managed through the transfer");
    }
}
