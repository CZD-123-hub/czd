package com.coding.assistant.exception;

import com.coding.assistant.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(msg -> msg != null && !msg.isBlank())
                .distinct()
                .collect(Collectors.toList());
        String message = String.join("; ", details);
        if (message.isBlank()) {
            message = "Request validation failed";
        }
        log.warn("Validation error: {}", message);
        return buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ErrorCode.BIZ_VALIDATION_FAILED,
                message,
                details.isEmpty() ? null : details
        );
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(msg -> msg != null && !msg.isBlank())
                .distinct()
                .collect(Collectors.toList());
        String message = String.join("; ", details);
        if (message.isBlank()) {
            message = "Request binding failed";
        }
        log.warn("Bind error: {}", message);
        return buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ErrorCode.BIZ_VALIDATION_FAILED,
                message,
                details.isEmpty() ? null : details
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String name = ex.getName() == null ? "parameter" : ex.getName();
        String message = String.format("Parameter type mismatch: %s", name);
        log.warn("Type mismatch: {}", message);
        return buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ErrorCode.BIZ_VALIDATION_FAILED,
                message,
                null
        );
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        int statusCode = ex.getCode() >= ErrorCode.BAD_REQUEST && ex.getCode() < 600
                ? ex.getCode()
                : ErrorCode.BAD_REQUEST;
        String bizCode = StringUtils.hasText(ex.getBizCode())
                ? ex.getBizCode()
                : ErrorCode.defaultBizCode(statusCode);
        String message = StringUtils.hasText(ex.getMessage())
                ? ex.getMessage()
                : ErrorCode.defaultMessage(statusCode);
        log.warn("Business error: code={}, bizCode={}, message={}", statusCode, bizCode, message);
        return buildErrorResponse(statusCode, bizCode, message, ex.getDetails());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication error: {}", ex.getMessage());
        return buildErrorResponse(
                ErrorCode.UNAUTHORIZED,
                ErrorCode.BIZ_UNAUTHORIZED,
                "Authentication failed. Please login again.",
                null
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildErrorResponse(
                ErrorCode.FORBIDDEN,
                ErrorCode.BIZ_FORBIDDEN,
                "Access denied",
                null
        );
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFoundException(NoHandlerFoundException ex) {
        log.warn("Not found: {}", ex.getRequestURL());
        return buildErrorResponse(
                ErrorCode.NOT_FOUND,
                ErrorCode.BIZ_NOT_FOUND,
                "Resource not found",
                null
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        log.warn("No acceptable representation: {}", ex.getMessage());
        return buildErrorResponse(
                ErrorCode.BAD_REQUEST,
                ErrorCode.BIZ_BAD_REQUEST,
                "Accept header does not match API response type",
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Internal server error", ex);
        return buildErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR,
                ErrorCode.BIZ_INTERNAL_SERVER_ERROR,
                "Internal server error. Please try again later.",
                null
        );
    }

    private ResponseEntity<ApiResponse<Void>> buildErrorResponse(int statusCode, String message) {
        return buildErrorResponse(statusCode, ErrorCode.defaultBizCode(statusCode), message, null);
    }

    private ResponseEntity<ApiResponse<Void>> buildErrorResponse(
            int statusCode,
            String bizCode,
            String message,
            Object details
    ) {
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            statusCode = ErrorCode.INTERNAL_SERVER_ERROR;
        }
        String safeBizCode = StringUtils.hasText(bizCode)
                ? bizCode
                : ErrorCode.defaultBizCode(statusCode);
        String safeMessage = StringUtils.hasText(message)
                ? message
                : ErrorCode.defaultMessage(statusCode);
        return ResponseEntity.status(status)
                .body(ApiResponse.error(statusCode, safeBizCode, safeMessage, details));
    }
}
