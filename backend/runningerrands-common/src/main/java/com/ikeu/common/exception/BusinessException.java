package com.ikeu.common.exception;

/**
 * 通用业务异常，用于表示业务规则校验失败等场景。
 * HTTP 状态码：400
 */
public class BusinessException extends BaseException {

    public BusinessException() {
        super("业务异常");
    }

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(int code, String message) {
        super(code, message);
    }
}
