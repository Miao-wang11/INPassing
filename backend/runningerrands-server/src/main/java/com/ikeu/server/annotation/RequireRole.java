package com.ikeu.server.annotation;

import java.lang.annotation.*;

/**
 * 角色权限注解，标记方法需要的管理员角色。
 * 默认仅超管(1)可访问，可通过 value 指定多个允许的角色。
 * @author ikeu
 * @since 2026/05/31
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    /** 允许访问的角色：1-超管，2-普通管理员 */
    int[] value() default {1};
}
