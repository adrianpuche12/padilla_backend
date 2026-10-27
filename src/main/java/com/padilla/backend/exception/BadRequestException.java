package com.padilla.backend.exception;

/** PRO-006: Pedido invalido (400). Lo traduce GlobalExceptionHandler. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
