package com.ikeu.common.exception;

/**
 * 未授权/登录失败异常，用于表示用户未登录或凭据无效。
 * HTTP 状态码：401
 */
public class UnauthorizedException extends BaseException {

    public UnauthorizedException() {
        super("未授权");
    }

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(int code, String message) {
        super(code, message);
    }
}
