package com.ikeu.server.interceptor;

import com.ikeu.common.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * STOMP 通道拦截器，在消息处理前从 session 中获取 userId 设置到 ThreadLocal。
 * @author ikeu
 * @since 2025/05/26
 */
@Slf4j
@Component
public class AuthChannelInterceptor implements ExecutorChannelInterceptor {

    /**
     * 在消息发送前记录 STOMP 命令和会话信息到日志。
     *
     * <p>从消息头中提取 STOMP 命令、会话 ID 和 session attributes 的键集合，
     * 输出到 debug 日志用于调试和问题排查。
     *
     * @param message STOMP 消息
     * @param channel 消息通道
     * @return 原始消息（不做修改）
     */
    @Override
    public Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && accessor.getCommand() != null) {
            Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
            log.debug("STOMP preSend: cmd={}, sessionId={}, hasSessionAttrs={}, attrsKeys={}, user={}",
                    accessor.getCommand(),
                    accessor.getSessionId(),
                    sessionAttrs != null,
                    sessionAttrs != null ? sessionAttrs.keySet() : null,
                    accessor.getUser());
        }
        return message;
    }

    /**
     * 在消息处理前从 session attributes 中提取 userId 并注入 ThreadLocal。
     *
     * <p>userId 由 {@link JwtHandshakeInterceptor} 在 WebSocket 握手阶段放入 session attributes。
     * 此处将其取出并通过 {@link BaseContext#setCurrentId} 设置到 ThreadLocal，
     * 使后续的消息处理方法可以通过 {@link BaseContext#getCurrentId()} 获取当前用户ID。
     *
     * @param message STOMP 消息
     * @param channel 消息通道
     * @param handler 消息处理器
     * @return 原始消息
     */
    @Override
    public Message<?> beforeHandle(@NotNull Message<?> message, @NotNull MessageChannel channel, @NotNull MessageHandler handler) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            log.warn("beforeHandle: accessor is null");
            return message;
        }

        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs == null) {
            log.warn("beforeHandle: sessionAttributes is null for cmd={}", accessor.getCommand());
            return message;
        }

        Object uid = sessionAttrs.get("userId");
        if (uid == null) {
            log.warn("beforeHandle: userId not found in session attributes, keys={}", sessionAttrs.keySet());
            return message;
        }

        Long userId = toLong(uid);
        if (userId != null) {
            BaseContext.setCurrentId(userId);
        } else {
            log.warn("beforeHandle: userId type is {}, expected Long/Integer", uid.getClass().getName());
        }

        return message;
    }

    /**
     * 消息处理完成后清理 ThreadLocal，防止内存泄漏。
     */
    @Override
    public void afterMessageHandled(@NotNull Message<?> message, @NotNull MessageChannel channel, @NotNull MessageHandler handler, Exception ex) {
        BaseContext.removeCurrentId();
    }

    /**
     * 将 Object 安全转换为 Long，兼容 Integer 和 Number 类型。
     *
     * @param value 待转换对象
     * @return 转换后的 Long，无法转换时返回 null
     */
    private Long toLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        return null;
    }
}
