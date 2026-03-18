package com.equiflow.order.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex, WebRequest request) {
        String message = "Invalid value for '" + ex.getName() + "'. Expected format: YYYY-MM-DD (e.g. 2026-03-17)";
        return errorResponse(HttpStatus.BAD_REQUEST, message, request.getDescription(false));
    }

    @ExceptionHandler(DateTimeException.class)
    public ResponseEntity<Map<String, Object>> handleDateFormat(DateTimeException ex, WebRequest request) {
        return errorResponse(HttpStatus.BAD_REQUEST, "Invalid date format. Expected YYYY-MM-DD (e.g. 2026-03-17)", request.getDescription(false));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getDescription(false));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex, WebRequest request) {
        return errorResponse(HttpStatus.CONFLICT, ex.getMessage(), request.getDescription(false));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return errorResponse(HttpStatus.BAD_REQUEST, message, request.getDescription(false));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request.getDescription(false));
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message, String path) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("timestamp", Instant.now());
        body.put("path", path);
        body.put("service", "order-service");
        return ResponseEntity.status(status).body(body);
    }
}
