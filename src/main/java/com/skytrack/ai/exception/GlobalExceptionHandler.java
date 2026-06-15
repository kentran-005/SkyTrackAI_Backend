package com.skytrack.ai.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<Map<String, String>> handleExternalService(ExternalServiceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentNotValidException.class,
            DataIntegrityViolationException.class
    })
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("error", readableMessage(ex)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Unexpected server error"));
    }

    private String readableMessage(Exception ex) {
        if (ex instanceof DataIntegrityViolationException) {
            return "Request violates database constraints";
        }
        if (ex instanceof MethodArgumentNotValidException validationException) {
            String message = validationException.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getDefaultMessage() == null
                            ? error.getField() + " is invalid"
                            : error.getDefaultMessage())
                    .distinct()
                    .collect(Collectors.joining(", "));
            return message.isBlank() ? "Invalid request" : message;
        }
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Invalid request"
                : ex.getMessage();
    }
}
