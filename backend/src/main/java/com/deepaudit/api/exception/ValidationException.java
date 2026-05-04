package com.deepaudit.api.exception;

import java.util.List;

/**
 * Carries one-or-more field-level / DSL-level errors. Translated to
 * HTTP 400 by {@link ApiExceptionHandler} with the full error list
 * preserved so the UI can highlight every offending sub-tree path.
 */
public class ValidationException extends RuntimeException {

    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super("Validation failed (" + errors.size() + " error" + (errors.size() == 1 ? "" : "s") + ")");
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
