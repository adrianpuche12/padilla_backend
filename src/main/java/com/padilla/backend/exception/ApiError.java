package com.padilla.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * PRO-006: formato unico de error del API. "message" se mantiene porque el frontend ya lo lee
 * (err.response.data.message).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(int status, String error, String message, Map<String, String> fields,
                       String path, OffsetDateTime timestamp) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(status, error, message, null, path, OffsetDateTime.now());
    }
}
