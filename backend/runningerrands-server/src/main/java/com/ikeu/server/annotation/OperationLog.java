package com.ikeu.server.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解，标记方法需要在执行成功后自动记录操作日志。
 * @author ikeu
 * @since 2026/06/02
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
    /** 操作模块 */
    String module();
    /** 操作类型 */
    String action();
    /** 操作描述，支持 SpEL 表达式引用方法参数，如 #userId */
    String description() default "";
}
