package com.aiworkmate.common;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应格式 — 所有接口返回此结构
 */
@Data
@NoArgsConstructor
public class Result<T> {

    private int code;
    private String errorCode;
    private String message;
    private T data;
    private String requestId;
    private String traceId;

    public Result(int code, String errorCode, String message, T data) {
        this.code = code;
        this.errorCode = errorCode;
        this.message = message;
        this.data = data;
        this.requestId = TraceContext.requestId();
        this.traceId = TraceContext.traceId();
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, null, "success", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(200, null, "success", null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, null, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(500, ErrorCode.SYSTEM_ERROR.getErrorCode(), message, null);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getDefaultMessage());
    }

    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), errorCode.getErrorCode(), message, null);
    }
}
