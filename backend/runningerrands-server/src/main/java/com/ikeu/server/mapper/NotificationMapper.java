package com.ikeu.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikeu.model.entity.Notification;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 通知 Mapper，提供通知实体的 CRUD 及过期通知清理操作。
 * @author ikeu
 * @since 2025/05/21
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 分批清理过期通知。
     *
     * <p>清理规则：已读超过30天、系统通知（type=1）超过7天、订单通知（type=2）超过90天。
     * 使用子查询 + LIMIT 实现分批删除，避免一次性删除大量数据锁表。
     *
     * @param batchSize 单批最大删除数
     * @return 实际删除行数
     */
    @Delete("DELETE FROM notification WHERE id IN ("
            + "SELECT id FROM ("
            + "  SELECT n.id FROM notification n"
            + "  WHERE (n.is_read = 1 AND n.created_at < DATE_SUB(NOW(), INTERVAL 30 DAY))"
            + "     OR (n.type = 1 AND n.created_at < DATE_SUB(NOW(), INTERVAL 7 DAY))"
            + "     OR (n.type = 2 AND n.created_at < DATE_SUB(NOW(), INTERVAL 90 DAY))"
            + "  LIMIT #{batchSize}"
            + ") t)")
    int deleteExpiredNotifications(@Param("batchSize") int batchSize);
}
