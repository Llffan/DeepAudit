package com.deepaudit.api.exception;

import com.deepaudit.service.MedicalRecordImportService.PayloadTooLargeException;
import com.deepaudit.service.MedicalRecordImportService.UnsupportedFileTypeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorBody> handleUnavailable(ServiceUnavailableException e) {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorBody("service_unavailable", e.getMessage(), List.of()));
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ErrorBody> handleUnsupportedType(UnsupportedFileTypeException e) {
        return ResponseEntity
            .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(new ErrorBody("unsupported_media_type", e.getMessage(), List.of()));
    }

    @ExceptionHandler({PayloadTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorBody> handlePayloadTooLarge(RuntimeException e) {
        // MaxUploadSizeExceededException carries the configured limit in
        // its default message, which is fine to surface verbatim.
        return ResponseEntity
            .status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new ErrorBody("payload_too_large", e.getMessage(), List.of()));
    }
}
