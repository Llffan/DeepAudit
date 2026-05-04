package com.deepaudit.api.exception;

/**
 * Translated to HTTP 503 by {@link ApiExceptionHandler}. Used when an
 * upstream dependency (currently: the LLM via langchain4j) is not
 * configured or is unreachable.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
