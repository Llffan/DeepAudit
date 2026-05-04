package com.deepaudit.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Translates the three custom domain exceptions to consistent JSON
 * error bodies. Frontend reads {@code errors} on 400 to render the
 * DSL-validation message list.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ErrorBody(String code, String message, List<String> errors) {
        public ErrorBody {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorBody> handleValidation(ValidationException e) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorBody("validation_failed", e.getMessage(), e.getErrors()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorBody> handleNotFound(NotFoundException e) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ErrorBody("not_found", e.getMessage(), List.of()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorBody> handleConflict(ConflictException e) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(new ErrorBody("conflict", e.getMessage(), List.of()));
    }
}
