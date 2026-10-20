package com.ikeu.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ikeu.model.dto.NotificationBroadcastDTO;
import com.ikeu.model.dto.NotificationSendDTO;
import com.ikeu.model.entity.User;
import com.ikeu.server.mapper.UserMapper;
import com.ikeu.server.service.AdminNotificationService;
import com.ikeu.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端消息管理服务实现，处理向指定用户发送通知和全站广播通知。
 * @author ikeu
 * @since 2026/06/15
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotificationServiceImpl implements AdminNotificationService {

    private final NotificationService notificationService;
    private final UserMapper userMapper;

    /**
     * 向指定的多个用户发送通知。
     *
     * <p>遍历用户 ID 列表逐条发送。对不存在的用户或已禁用的用户（status != 1）跳过并记录警告，
     * 单个用户发送异常时捕获并记录错误日志，避免因单个用户失败阻塞整个批量操作。
     *
     * @param dto 通知发送参数，包含目标用户 ID 列表、通知类型、标题和内容
     */
    @Override
    public void sendToUsers(NotificationSendDTO dto) {
        for (Long userId : dto.getUserIds()) {
            try {
                User user = userMapper.selectById(userId);
                if (user == null || !user.getStatus().equals(1)) {
                    log.warn("用户 {} 不存在或已禁用，跳过发送通知", userId);
                    continue;
                }
                notificationService.sendNotification(userId, dto.getType(), dto.getTitle(), dto.getContent(), null);
            } catch (Exception e) {
                log.error("向用户 {} 发送通知失败", userId, e);
            }
        }
        log.info("管理员向 {} 个用户发送了通知", dto.getUserIds().size());
    }

    /**
     * 向所有状态正常的用户（status = 1）全站广播通知。
     *
     * <p>查询全部启用用户列表后逐条发送。单个用户发送异常时捕获并记录错误日志，
     * 确保其他用户的通知不受影响。广播完成后打印实际发送的用户总数。
     *
     * @param dto 广播通知参数，包含通知类型、标题和内容
     */
    @Override
    public void broadcastToAll(NotificationBroadcastDTO dto) {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
        for (User user : users) {
            try {
                notificationService.sendNotification(user.getId(), dto.getType(), dto.getTitle(), dto.getContent(), null);
            } catch (Exception e) {
                log.error("向用户 {} 广播通知失败", user.getId(), e);
            }
        }
        log.info("管理员向 {} 个用户广播了通知", users.size());
    }
}
