package com.fintrack.common.exception;

/** Input that bean validation cannot express on its own (cross-field or clock-dependent). Maps to HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
