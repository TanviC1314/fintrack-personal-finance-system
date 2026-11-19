package com.fintrack.auth.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        // One message for "unknown email" and "wrong password" so the API does not reveal which emails exist.
        super("Invalid email or password");
    }
}
