package com.company.report.shared.error;

public class BusinessException extends RuntimeException {
    private final int statusCode;
    private final int code;

    public BusinessException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.code = statusCode;
    }

    public BusinessException(int statusCode, ErrorCode errorCode) {
        super(errorCode.message());
        this.statusCode = statusCode;
        this.code = errorCode.code();
    }

    public int statusCode() {
        return statusCode;
    }

    public int code() {
        return code;
    }
}
