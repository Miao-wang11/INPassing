package com.ikeu.common.exception;

/**
 * 参数校验异常，用于表示请求参数格式或值不合法。
 * HTTP 状态码：400
 */
public class ParamErrorException extends BaseException {

    public ParamErrorException() {
        super("参数错误");
    }

    public ParamErrorException(String message) {
        super(message);
    }

    public ParamErrorException(int code, String message) {
        super(code, message);
    }
}
