package com.ikeu.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.constant.StatusConstant;
import com.ikeu.common.exception.NotFoundException;
import com.ikeu.common.result.PageResult;
import com.ikeu.model.entity.Notification;
import com.ikeu.model.vo.NotificationVO;
import com.ikeu.server.mapper.NotificationMapper;
import com.ikeu.server.service.NotificationService;
import com.ikeu.server.websocket.WebSocketUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知服务实现，处理通知的发送、分页查询、已读标记、删除和过期清理。
 * @author ikeu
 * @since 2026/05/14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final WebSocketUtil webSocketUtil;

    /**
     * 发送通知并持久化，同时通过 WebSocket 实时推送给用户。
     *
     * <p>构建 Notification 实体（isRead=未读），插入数据库后通过 WebSocketUtil
     * 推送到目标用户的 /notification 订阅路径。WebSocket 推送失败仅记录 error 日志，
     * 不影响通知持久化结果。
     *
     * @param userId 接收者用户ID
     * @param type 通知类型
     * @param title 通知标题
     * @param content 通知内容
     * @param targetId 关联业务ID（任务/订单ID）
     */
    @Override
    @Transactional
    public void sendNotification(Long userId, Integer type, String title, String content, Long targetId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .content(content)
                .isRead(StatusConstant.UNREAD)
                .targetId(targetId)
                .createdAt(LocalDateTime.now())
                .build();
        notificationMapper.insert(notification);
        log.info("发送通知给用户 {}：{}", userId, title);

        try {
            NotificationVO vo = BeanUtil.copyProperties(notification, NotificationVO.class);
            webSocketUtil.pushNotification(userId, vo);
        } catch (Exception e) {
            log.error("WebSocket 推送失败", e);
        }
    }

    /**
     * 分页查询用户通知列表，支持按已读状态筛选，按创建时间倒序排列。
     *
     * @param userId 用户ID
     * @param isRead 已读状态筛选（null 表示不限制）
     * @param page 页码
     * @param size 每页条数
     * @return 分页通知列表
     */
    @Override
    public PageResult<NotificationVO> listNotifications(Long userId, Integer isRead, int page, int size) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(isRead != null, Notification::getIsRead, isRead)
                .orderByDesc(Notification::getCreatedAt);
        Page<Notification> p = page(new Page<>(page, size), wrapper);

        List<NotificationVO> records = p.getRecords().stream()
                .map(n -> BeanUtil.copyProperties(n, NotificationVO.class))
                .collect(Collectors.toList());

        return new PageResult<>(p.getTotal(), records);
    }

    /**
     * 标记单条通知为已读，使用条件更新确保仅更新未读状态的通知。
     *
     * <p>通过 LambdaUpdateWrapper 同时匹配 id、userId 和 isRead=未读条件，
     * 防止越权操作或重复标记。
     *
     * @param userId 用户ID
     * @param notificationId 通知ID
     */
    @Override
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, StatusConstant.UNREAD)
                .set(Notification::getIsRead, StatusConstant.READ);
        update(wrapper);
    }

    /**
     * 将该用户所有未读通知批量更新为已读。
     *
     * @param userId 用户ID
     */
    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, StatusConstant.UNREAD)
                .set(Notification::getIsRead, StatusConstant.READ);
        update(wrapper);
    }

    /**
     * 删除通知，校验通知存在且属于当前用户后物理删除。
     *
     * @param userId 用户ID
     * @param notificationId 通知ID
     */
    @Override
    @Transactional
    public void markBatchRead(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(Notification::getId, ids)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, StatusConstant.UNREAD)
                .set(Notification::getIsRead, StatusConstant.READ);
        update(wrapper);
    }

    @Override
    @Transactional
    public void deleteNotification(Long userId, Long notificationId) {
        Notification notification = getById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            throw new NotFoundException(MessageConstant.ERROR);
        }
        removeById(notificationId);
    }

    /**
     * 分批清理过期通知，委托 Mapper 层执行删除并返回当次删除行数。
     *
     * <p>每次删除固定 batchSize 条，由定时任务循环调用直到无更多过期数据，
     * 避免一次性删除大量数据导致长事务锁表。
     *
     * @param batchSize 每批处理数量
     * @return 本次清理的通知条数
     */
    @Override
    @Transactional
    public int cleanupExpiredNotifications(int batchSize) {
        int deleted = notificationMapper.deleteExpiredNotifications(batchSize);
        if (deleted > 0) {
            log.info("清理了 {} 条过期通知", deleted);
        }
        return deleted;
    }

    @Override
    public long getUnreadCount(Long userId) {
        return lambdaQuery()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, StatusConstant.UNREAD)
                .count();
    }
}
