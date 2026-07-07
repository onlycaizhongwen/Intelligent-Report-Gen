package com.company.report.shared.error;

import com.company.report.shared.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        log.warn("业务异常: code={}, message={}", ex.code(), ex.getMessage());
        return ResponseEntity.status(ex.statusCode()).body(ApiResponse.failure(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("参数校验失败: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                ApiResponse.failure(ErrorCode.PARAMETER_INVALID.code(), ErrorCode.PARAMETER_INVALID.message())
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("request parameter type mismatch: name={}, value={}", ex.getName(), ex.getValue());
        return ResponseEntity.badRequest().body(
                ApiResponse.failure(ErrorCode.PARAMETER_INVALID.code(), ErrorCode.PARAMETER_INVALID.message())
        );
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSecurity(SecurityException ex) {
        log.warn("访问被拒绝: {}", ex.getMessage());
        if (isShareChallengeRequired(ex.getMessage())) {
            return ResponseEntity.status(428).body(ApiResponse.failure(428, "share access challenge required"));
        }
        if (isShareRateLimited(ex.getMessage())) {
            return ResponseEntity.status(429).body(ApiResponse.failure(429, "share access rate limited"));
        }
        return ResponseEntity.status(403).body(
                ApiResponse.failure(ErrorCode.FORBIDDEN.code(), ErrorCode.FORBIDDEN.message())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("非法请求: {}", ex.getMessage());
        if (isNotFound(ex.getMessage())) {
            return ResponseEntity.status(404).body(ApiResponse.failure(404, ex.getMessage()));
        }
        return ResponseEntity.badRequest().body(ApiResponse.failure(400, ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.warn("资源状态冲突: {}", ex.getMessage());
        return ResponseEntity.status(409).body(ApiResponse.failure(409, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("系统异常", ex);
        return ResponseEntity.internalServerError().body(
                ApiResponse.failure(ErrorCode.SYSTEM_BUSY.code(), ErrorCode.SYSTEM_BUSY.message())
        );
    }

    private boolean isNotFound(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.toLowerCase().contains("not found");
    }

    private boolean isShareRateLimited(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.toLowerCase().startsWith("share access rate limited");
    }

    private boolean isShareChallengeRequired(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.toLowerCase().startsWith("share access challenge required");
    }
}
