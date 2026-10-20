package com.ikeu.server.task;

import com.ikeu.common.constant.RedisConstant;
import com.ikeu.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 通知清理定时任务，每日凌晨3点分批清理过期通知。
 * @author ikeu
 * @since 2026/05/14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupTask {

    private final NotificationService notificationService;
    private final RedissonClient redissonClient;

    private static final int BATCH_SIZE = 500;
    private static final int MAX_BATCHES = 20;

    /**
     * 分批删除过期通知，使用 Redisson 分布式锁防止多实例重复执行。
     *
     * <p>执行流程：
     * <ol>
     *   <li>获取 Redisson 分布式锁 NOTIFICATION_CLEANUP_LOCK_KEY，tryLock 等待0秒、持有60秒，
     *       未获取到锁说明其他实例正在执行，跳过本次</li>
     *   <li>循环最多 MAX_BATCHES 次，每次调用 NotificationService 删除 BATCH_SIZE 条过期通知</li>
     *   <li>当单次删除数小于 BATCH_SIZE 时说明已无更多过期数据，提前结束循环</li>
     *   <li>finally 中判断锁是否由当前线程持有，若是则释放</li>
     * </ol>
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredNotifications() {
        RLock lock = redissonClient.getLock(RedisConstant.NOTIFICATION_CLEANUP_LOCK_KEY);
        try {
            if (!lock.tryLock(0, 60, TimeUnit.SECONDS)) {
                log.info("通知清理任务已被其他实例执行，跳过");
                return;
            }

            log.info("开始执行过期通知清理任务");
            int totalDeleted = 0;
            for (int i = 0; i < MAX_BATCHES; i++) {
                int deleted = notificationService.cleanupExpiredNotifications(BATCH_SIZE);
                totalDeleted += deleted;
                if (deleted < BATCH_SIZE) {
                    break;
                }
            }
            log.info("过期通知清理完成，共删除 {} 条", totalDeleted);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("通知清理任务被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
