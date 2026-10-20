package com.ikeu.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikeu.model.dto.OnTimeStatsDTO;
import com.ikeu.model.entity.TaskOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 任务订单 Mapper，提供订单实体的基础 CRUD 操作。
 * @author ikeu
 * @since 2025/05/21
 */
@Mapper
public interface TaskOrderMapper extends BaseMapper<TaskOrder> {

    /**
     * 条件更新订单状态：仅在订单当前状态符合预期时才更新，用于防止并发覆盖。
     *
     * @param orderId 订单ID
     * @param expectedStatus 期望的当前状态
     * @param targetStatus 目标状态
     * @param cancelReason 取消原因
     * @return 影响行数（0表示状态已被其他操作变更）
     */
    @Update("UPDATE task_order SET status = #{targetStatus}, cancel_reason = #{cancelReason} " +
            "WHERE id = #{orderId} AND status = #{expectedStatus}")
    int updateStatusIf(@Param("orderId") Long orderId,
                       @Param("expectedStatus") Integer expectedStatus,
                       @Param("targetStatus") Integer targetStatus,
                       @Param("cancelReason") String cancelReason);

    /**
     * 统计跑腿员的准时完成率数据。
     *
     * <p>数据库侧执行 {@code COUNT(*) AS total} 和
     * {@code SUM(CASE WHEN confirm_time <= expect_finish_time THEN 1 ELSE 0 END) AS on_time}，
     * 调用方按 {@code onTime / total * 100} 计算准时率百分比。
     *
     * @param runnerId 跑腿员用户ID
     * @return 含 total 和 onTime 的统计对象
     */
    OnTimeStatsDTO countCompletedOnTime(@Param("runnerId") Long runnerId);

    /**
     * 查询指定任务的最新一条有效订单。
     *
     * <p>按 ID 倒序取第一条，仅查询未删除（is_deleted=0）的记录，
     * 替代 {@code selectOne(...).last("LIMIT 1")} 的裸 SQL 注入写法。
     *
     * @param taskId 任务ID
     * @return 最新订单实体，无记录时返回 null
     */
    TaskOrder selectLatestByTaskId(@Param("taskId") Long taskId);
}
