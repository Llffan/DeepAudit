package com.deepaudit.api.exception;

/**
 * Translated to HTTP 409 by {@link ApiExceptionHandler}. Used for
 * unique-constraint conflicts (duplicate rule code) and policy
 * conflicts (deleting an enabled rule).
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
