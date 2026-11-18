package com.fincore.platform.web;

import java.util.List;

import com.fincore.platform.api.ApiError;
import com.fincore.platform.api.ApiMetaFactory;
import com.fincore.platform.api.ApiResponse;
import com.fincore.platform.api.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldViolation> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage() == null
                        ? "invalid value"
                        : error.getDefaultMessage()))
                .toList();
        return response(HttpStatus.BAD_REQUEST,
                new ApiError("VALIDATION_ERROR", "Request validation failed", details), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST,
                new ApiError("MALFORMED_REQUEST", "Request body could not be read", List.of()), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessRule(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY,
                new ApiError("BUSINESS_RULE_VIOLATION", exception.getMessage(), List.of()), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND,
                new ApiError("RESOURCE_NOT_FOUND", exception.getMessage(), List.of()), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(
            ConflictException exception,
            HttpServletRequest request) {
        return response(HttpStatus.CONFLICT,
                new ApiError("CONFLICT", exception.getMessage(), List.of()), request);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationFailed(
            AuthenticationFailedException exception,
            HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED,
                new ApiError("UNAUTHORIZED", "Authentication failed", List.of()), request);
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleUpstreamFailure(
            UpstreamServiceException exception,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_GATEWAY,
                new ApiError("AUTH_PROVIDER_UNAVAILABLE", "Authentication provider is unavailable", List.of()),
                request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST,
                new ApiError("MISSING_HEADER", "Required request header is missing", List.of()), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, new ApiError("FORBIDDEN", "Access denied", List.of()), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        LOGGER.error("unhandled_request_error path={} errorType={}",
                request.getRequestURI(), exception.getClass().getName(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR,
                new ApiError("INTERNAL_ERROR", "An unexpected error occurred", List.of()), request);
    }

    private static ResponseEntity<ApiResponse<Void>> response(
            HttpStatus status,
            ApiError error,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiResponse.failure(error, ApiMetaFactory.from(request)));
    }
}
