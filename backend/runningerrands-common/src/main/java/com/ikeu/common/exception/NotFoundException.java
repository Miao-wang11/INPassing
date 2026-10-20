package com.ikeu.common.exception;

/**
 * 资源未找到异常。
 * HTTP 状态码：404
 */
public class NotFoundException extends BaseException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(int code, String message) {
        super(code, message);
    }
}
