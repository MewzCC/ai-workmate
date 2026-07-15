package com.aiworkmate.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String errorCode;
    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.code = status.value();
        this.errorCode = null;
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.status = errorCode.getStatus();
        this.code = errorCode.getCode();
        this.errorCode = errorCode.getErrorCode();
    }
}
