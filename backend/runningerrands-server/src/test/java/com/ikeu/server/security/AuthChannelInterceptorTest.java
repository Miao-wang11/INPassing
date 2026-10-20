package com.ikeu.server.security;

import com.ikeu.server.interceptor.AuthChannelInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Inbound STOMP permissions must be checked on the server, not in the client. */
class AuthChannelInterceptorTest {
    private final AuthChannelInterceptor interceptor = new AuthChannelInterceptor();
    private final MessageChannel channel = mock(MessageChannel.class);

    private Message<byte[]> frame(StompCommand command, Long userId, String destination) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        if (userId != null) headers.setSessionAttributes(Map.of("userId", userId));
        if (destination != null) headers.setDestination(destination);
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test
    void connectAssignsBrokerPrincipalFromVerifiedHandshake() {
        Message<byte[]> message = frame(StompCommand.CONNECT, 42L, null);
        assertSame(message, interceptor.preSend(message, channel));
        StompHeaderAccessor headers = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        assertNotNull(headers);
        assertNotNull(headers.getUser());
        assertEquals("42", headers.getUser().getName());
    }

    @Test
    void invalidOrMissingHandshakeIdentityIsRejected() {
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(frame(StompCommand.CONNECT, null, null), channel));
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, 0L, "/user/queue/chat"), channel));
        assertThrows(MessagingException.class,
                () -> interceptor.preSend(frame(StompCommand.SEND, -1L, "/app/chat.send"), channel));
    }

    @Test
    void clientCannotPublishDirectlyToBrokerOrOtherApplications() {
        for (String destination : new String[]{
                "/queue/chat", "/user/42/queue/chat", "/topic/all", "/app/unknown"}) {
            assertThrows(MessagingException.class,
                    () -> interceptor.preSend(frame(StompCommand.SEND, 42L, destination), channel),
                    destination);
        }
        assertDoesNotThrow(() ->
                interceptor.preSend(frame(StompCommand.SEND, 42L, "/app/chat.send"), channel));
    }

    @Test
    void subscriptionsMustUseAnAuthenticatedPrivateUserDestination() {
        for (String destination : new String[]{
                "/topic/all", "/queue/chat", "/user/99/queue/chat", "/user/42/notification",
                "/user/queue/other", "/user"}) {
            assertThrows(MessagingException.class,
                    () -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, 42L, destination), channel),
                    destination);
        }
        for (String destination : new String[]{
                "/user/queue/chat", "/user/queue/notification", "/user/notification"}) {
            assertDoesNotThrow(() ->
                    interceptor.preSend(frame(StompCommand.SUBSCRIBE, 42L, destination), channel));
        }
    }
}
