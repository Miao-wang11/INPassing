package com.ikeu.server.annotation;

import java.lang.annotation.*;

/**
 * 标记方法需要在执行成功后发送通知。
 *
 * <p>被标记的方法执行成功后会由 {@code NotificationAspect} 切面拦截，
 * 根据 targetUserType 确定通知接收者、noticeType 确定通知类型、
 * title 和 content 作为通知模板，自动创建并推送站内通知。
 * @author ikeu
 * @since 2025/05/21
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SendNotification {
    /** 接收通知的用户类型：1-发布者，2-跑腿员 */
    int targetUserType();
    /** 通知类型 */
    int noticeType();
    /** 通知标题模板 */
    String title();
    /** 通知内容模板 */
    String content();
}
