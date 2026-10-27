package com.padilla.backend.exception;

/** PRO-006: Conflicto con el estado actual de los datos (409). Lo traduce GlobalExceptionHandler. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
