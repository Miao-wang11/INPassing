package com.ikeu.server.service.impl;

import com.ikeu.model.entity.Task;
import com.ikeu.model.entity.User;
import com.ikeu.model.vo.TaskDetailVO;
import com.ikeu.server.mapper.*;
import com.ikeu.server.service.PaymentService;
import com.ikeu.server.service.CampusCatalogService;
import com.ikeu.server.util.RedisDefendUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression: owner- and stranger-specific task DTOs must never share a
 * taskId-only cache entry (which could leak pickup codes to strangers).
 */
class TaskDetailPrivacyTest {
    private TaskMapper taskMapper;
    private TaskServiceImpl service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(TaskMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        CampusCatalogService campuses = mock(CampusCatalogService.class);
        service = new TaskServiceImpl(taskMapper, campuses, userMapper,
                mock(UserAddressMapper.class), mock(TaskImageMapper.class),
                mock(ReviewMapper.class), mock(PaymentService.class),
                mock(RedissonClient.class), mock(CacheManager.class),
                mock(StringRedisTemplate.class), mock(RedisDefendUtil.class));

        Task task = new Task();
        task.setId(7L);
        task.setPublisherId(11L);
        task.setPickupCode("PICKUP-ONLY-OWNER");
        task.setPrivateNote("DO-NOT-SHARE");
        task.setContactName("李同学");
        task.setContactPhone("13800000001");
        when(taskMapper.selectById(7L)).thenReturn(task);

        User owner = new User();
        owner.setId(11L);
        owner.setNickname("发布者");
        when(userMapper.selectById(11L)).thenReturn(owner);
    }

    @Test
    void ownerThenVisitorNeverLeaksPrivateInformation() {
        TaskDetailVO owner = service.getTaskDetail(7L, 11L);
        assertTrue(owner.getIsOwner());
        assertEquals("PICKUP-ONLY-OWNER", owner.getPickupCode());

        TaskDetailVO stranger = service.getTaskDetail(7L, 22L);
        assertFalse(stranger.getIsOwner());
        assertNull(stranger.getPickupCode());
        assertEquals("", stranger.getPrivateNote());
        assertNotEquals("13800000001", stranger.getContactPhone());
        verify(taskMapper, times(2)).selectById(7L);
    }

    @Test
    void strangerThenOwnerGetsCorrectUnmaskedView() {
        TaskDetailVO stranger = service.getTaskDetail(7L, 22L);
        assertNull(stranger.getPickupCode());

        TaskDetailVO owner = service.getTaskDetail(7L, 11L);
        assertEquals("PICKUP-ONLY-OWNER", owner.getPickupCode());
        assertEquals("DO-NOT-SHARE", owner.getPrivateNote());
    }
}
