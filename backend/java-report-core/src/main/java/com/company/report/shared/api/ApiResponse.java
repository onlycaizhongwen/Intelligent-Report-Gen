package com.company.report.shared.api;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
        int code,
        String message,
        T data,
        OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "操作成功", data, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> failure(int code, String message) {
        return new ApiResponse<>(code, message, null, OffsetDateTime.now());
    }
}
