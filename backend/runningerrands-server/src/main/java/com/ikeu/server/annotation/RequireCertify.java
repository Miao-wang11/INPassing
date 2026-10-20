package com.ikeu.server.annotation;

import java.lang.annotation.*;

/**
 * 标记需要用户实名认证才能访问的接口。
 *
 * <p>被标记的方法在执行前会由 {@code CertifyAspect} 切面拦截，
 * 校验当前用户的实名认证状态，未认证用户访问时返回错误。
 * @author ikeu
 * @since 2025/05/21
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireCertify {
}
