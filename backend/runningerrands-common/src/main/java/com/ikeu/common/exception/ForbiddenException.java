package com.ikeu.common.exception;

/**
 * 禁止访问异常，用于表示用户无权限执行当前操作。
 * HTTP 状态码：403
 */
public class ForbiddenException extends BaseException {

    public ForbiddenException() {
        super("禁止访问");
    }

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(int code, String message) {
        super(code, message);
    }
}
