package com.ikeu.common.exception;

import lombok.Getter;

/**
 * 自定义异常基类，所有业务异常均继承此类。
 * 包含可选的业务错误码，默认为 0。
 */
@Getter
public abstract class BaseException extends RuntimeException {

    private final int code;

    protected BaseException(String message) {
        super(message);
        this.code = 0;
    }

    protected BaseException(int code, String message) {
        super(message);
        this.code = code;
    }

}
