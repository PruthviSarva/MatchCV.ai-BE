package com.tailorresume.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns every exception into an {@link ApiError}. Internal detail is logged
 * server-side and never returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(
                        ex.getStatus().value(),
                        ex.getStatus().getReasonPhrase(),
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiError.validation(request.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", "Malformed request body.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiError.of(413, "Payload Too Large",
                        "That file is too large. Upload a resume under 10 MB.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "Forbidden", "You do not have access to this resource.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        log.warn("Constraint violation on {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", "That record conflicts with an existing one.",
                        request.getRequestURI()));
    }

    /**
     * Unmapped path. Spring MVC 7 lets the request fall through to the resource
     * handler, which throws {@link NoResourceFoundException} rather than
     * {@link NoHandlerFoundException}. Both are handled so an unknown URL is a
     * 404 instead of a 500 from the generic handler.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNoHandler(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", "No such endpoint.", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        // The only place a full stack trace is allowed: the server log.
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error", "Something went wrong.",
                        request.getRequestURI()));
    }
}
