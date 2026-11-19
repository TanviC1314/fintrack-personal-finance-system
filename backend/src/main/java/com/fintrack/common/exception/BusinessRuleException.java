package com.fintrack.common.exception;

/** A well-formed request that breaks a business rule (for example insufficient funds). Maps to HTTP 422. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
