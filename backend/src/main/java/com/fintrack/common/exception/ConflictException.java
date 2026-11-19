package com.fintrack.common.exception;

/** Base for business-rule violations that map to HTTP 409 (the request is valid but conflicts with current state). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
