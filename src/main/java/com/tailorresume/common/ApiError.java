package com.tailorresume.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * The single error shape every failing request returns, so the frontend has
 * exactly one thing to parse.
 *
 * <p>{@code fieldErrors} is populated only for validation failures and omitted
 * otherwise. Internal detail (stack traces, SQL, provider messages) never
 * appears here — see {@link GlobalExceptionHandler}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError validation(String path, Map<String, String> fieldErrors) {
        return new ApiError(
                Instant.now(), 400, "Bad Request", "Validation failed.", path, fieldErrors);
    }
}
