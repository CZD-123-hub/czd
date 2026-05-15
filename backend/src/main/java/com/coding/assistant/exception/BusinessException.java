package com.coding.assistant.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String bizCode;
    private final Object details;

    public BusinessException(int code, String message) {
        this(code, ErrorCode.defaultBizCode(code), message, null);
    }

    public BusinessException(int code, String bizCode, String message) {
        this(code, bizCode, message, null);
    }

    public BusinessException(int code, String bizCode, String message, Object details) {
        super(message);
        this.code = code;
        this.bizCode = bizCode;
        this.details = details;
    }

    public BusinessException(String message) {
        this(ErrorCode.BAD_REQUEST, ErrorCode.BIZ_BAD_REQUEST, message, null);
    }
}
