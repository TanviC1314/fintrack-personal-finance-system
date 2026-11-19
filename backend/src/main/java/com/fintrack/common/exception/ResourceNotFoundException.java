package com.fintrack.common.exception;

/**
 * Thrown when a resource does not exist or is not visible to the caller. Using one
 * exception for both cases avoids revealing that another user's resource exists.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
