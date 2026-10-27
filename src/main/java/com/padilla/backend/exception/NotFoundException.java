package com.padilla.backend.exception;

/** PRO-006: Recurso inexistente (404). Lo traduce GlobalExceptionHandler. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
