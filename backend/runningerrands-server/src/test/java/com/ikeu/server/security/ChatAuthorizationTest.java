package com.ikeu.server.security;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ikeu.common.exception.BusinessException;
import com.ikeu.common.exception.ForbiddenException;
import com.ikeu.model.entity.ChatMessage;
import com.ikeu.model.entity.User;
import com.ikeu.server.mapper.ChatMessageMapper;
import com.ikeu.server.mapper.UserMapper;
import com.ikeu.server.service.CampusCatalogService;
import com.ikeu.server.service.impl.ChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Covers the same authorization boundary for send, history and read receipts. */
class ChatAuthorizationTest {
    private ChatMessageMapper messages;
    private UserMapper users;
    private CampusCatalogService campuses;
    private SimpMessagingTemplate broker;
    private ChatServiceImpl service;

    private User target(int certified, int enabled, long campusId) {
        return User.builder().id(22L).nickname("CI receiver").status(enabled)
                .isCertify(certified).campusId(campusId).build();
    }

    @BeforeEach
    void setup() {
        messages = mock(ChatMessageMapper.class);
        users = mock(UserMapper.class);
        campuses = mock(CampusCatalogService.class);
        broker = mock(SimpMessagingTemplate.class);
        service = new ChatServiceImpl(messages, users, campuses, broker);
        when(campuses.requireUserCampus(11L)).thenReturn(10001L);
    }

    private void assertAllRoutesDenied() {
        assertThrows(ForbiddenException.class,
                () -> service.sendMessage(11L, 22L, "hello", 1));
        assertThrows(ForbiddenException.class,
                () -> service.getHistory(11L, 22L, 1, 20));
        assertThrows(ForbiddenException.class,
                () -> service.markRead(11L, 22L));
        verify(messages, never()).insert(any(ChatMessage.class));
        verify(messages, never()).markRead(anyLong(), anyLong());
        verify(messages, never()).selectHistory(any(), anyLong(), anyLong());
        verifyNoInteractions(broker);
    }

    @Test
    void otherCampusIsDeniedOnEveryRoute() {
        when(users.selectById(22L)).thenReturn(target(2, 1, 10002L));
        assertAllRoutesDenied();
    }

    @Test
    void disabledOrUncertifiedTargetIsDenied() {
        when(users.selectById(22L)).thenReturn(target(1, 1, 10001L));
        assertAllRoutesDenied();
    }

    @Test
    void selfConversationIsDenied() {
        assertThrows(ForbiddenException.class, () -> service.sendMessage(11L, 11L, "hello", 1));
        assertThrows(ForbiddenException.class, () -> service.getHistory(11L, 11L, 1, 20));
        assertThrows(ForbiddenException.class, () -> service.markRead(11L, 11L));
        verifyNoInteractions(messages, broker);
    }

    @Test
    void validSameCampusConversationPersistsAndRoutesPrivately() {
        when(users.selectById(22L)).thenReturn(target(2, 1, 10001L));
        when(users.selectById(11L)).thenReturn(User.builder().id(11L).nickname("CI sender").build());
        service.sendMessage(11L, 22L, "hello", 1);
        verify(messages).insert(argThat(m -> m.getSenderId().equals(11L)
                && m.getReceiverId().equals(22L) && "hello".equals(m.getContent())));
        verify(broker).convertAndSendToUser(eq("22"), eq("/queue/chat"), any(Object.class));
        verify(broker).convertAndSendToUser(eq("11"), eq("/queue/chat"), any(Object.class));

        Page<ChatMessage> empty = new Page<>(1, 20);
        when(messages.selectHistory(any(), eq(11L), eq(22L))).thenReturn(empty);
        assertTrue(service.getHistory(11L, 22L, 1, 20).isEmpty());
        service.markRead(11L, 22L);
        verify(messages).markRead(11L, 22L);
    }

    @Test
    void payloadMustFitDatabaseAndAllowlistedMessageTypes() {
        when(users.selectById(22L)).thenReturn(target(2, 1, 10001L));
        assertThrows(BusinessException.class, () -> service.sendMessage(11L, 22L, " ", 1));
        assertThrows(BusinessException.class, () -> service.sendMessage(11L, 22L, "a".repeat(501), 1));
        assertThrows(BusinessException.class, () -> service.sendMessage(11L, 22L, "hello", 9));
        assertThrows(BusinessException.class, () -> service.sendMessage(11L, 22L, "http://bad.test/image", 2));
        verify(messages, never()).insert(any(ChatMessage.class));
        verifyNoInteractions(broker);
    }
}
