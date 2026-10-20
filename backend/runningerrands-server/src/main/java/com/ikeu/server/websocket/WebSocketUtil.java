package com.ikeu.server.websocket;

import com.ikeu.model.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket 推送工具，通过 STOMP 向指定用户实时推送通知消息。
 * @author ikeu
 * @since 2025/05/26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketUtil {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 向指定用户推送实时通知。
     *
     * <p>通过 {@link SimpMessagingTemplate#convertAndSendToUser} 将通知消息
     * 发送到目标用户的 STOMP 订阅路径 {@code /user/{userId}/notification}，
     * 用户端需订阅该路径才能实时接收。
     *
     * @param userId 目标用户ID
     * @param notification 通知内容 VO
     */
    public void pushNotification(Long userId, NotificationVO notification) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/notification", notification);
    }
}
