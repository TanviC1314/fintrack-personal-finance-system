package com.fintrack.transfer.exception;

import com.fintrack.common.exception.ConflictException;

/** The idempotency key was already used for a transfer with different accounts or amount. */
public class IdempotencyKeyReusedException extends ConflictException {

    public IdempotencyKeyReusedException() {
        super("This Idempotency-Key was already used for a different transfer. Use a new key for a new transfer.");
    }
}
