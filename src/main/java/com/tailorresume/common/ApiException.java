package com.tailorresume.common;

import org.springframework.http.HttpStatus;

/**
 * Business failures that map to a specific HTTP status. Thrown by services and
 * translated into an {@link ApiError} by {@link GlobalExceptionHandler}.
 *
 * <p>Messages here are user-facing and safe to return. Anything sensitive is
 * logged, not thrown.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    /**
     * Quota exhausted. 402 rather than 403 so the frontend can tell "upgrade
     * your plan" apart from "you may not touch this resource".
     */
    public static ApiException paymentRequired(String message) {
        return new ApiException(HttpStatus.PAYMENT_REQUIRED, message);
    }

    /** The rewriting provider failed. The upstream detail is logged, not returned. */
    public static ApiException upstreamFailure(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, message);
    }
}
