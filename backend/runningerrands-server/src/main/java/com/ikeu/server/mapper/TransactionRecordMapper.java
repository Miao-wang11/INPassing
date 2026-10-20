package com.ikeu.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ikeu.model.entity.TransactionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 资金流水 Mapper，提供交易记录实体的基础 CRUD 及聚合查询。
 * @author ikeu
 * @since 2025/05/21
 */
@Mapper
public interface TransactionRecordMapper extends BaseMapper<TransactionRecord> {

    /**
     * 查询单个用户的累计跑腿收入。
     *
     * <p>数据库侧执行 {@code SELECT COALESCE(SUM(amount), 0)}，仅返回聚合值，
     * 避免全字段拉取到内存再求和。
     *
     * @param userId 用户ID
     * @return 累计跑腿收入（无记录时返回 0）
     */
    BigDecimal sumIncomeByUserId(@Param("userId") Long userId);

    /**
     * 批量查询多个用户的累计跑腿收入。
     *
     * <p>数据库侧执行 {@code SELECT user_id, SUM(amount) GROUP BY user_id}，
     * 返回每行包含 {@code user_id} 和 {@code total_income} 两个字段的 Map 列表。
     * 调用方需自行转换为 {@code Map<Long, BigDecimal>}。
     *
     * @param userIds 用户ID列表
     * @return 每行含 user_id 和 total_income 的 Map 列表
     */
    List<Map<String, Object>> sumIncomeByUserIds(@Param("userIds") List<Long> userIds);

    /**
     * 查询用户全量流水汇总，返回包含总收入（total_income）和总支出（total_expense）的 Map。
     *
     * <p>数据库侧执行聚合查询（SUM + GROUP BY），仅返回聚合值，避免全字段拉取到内存再求和。
     * 无记录时返回 null，由调用方处理零值兜底。
     *
     * @param userId 用户ID
     * @return 包含 total_income 和 total_expense 的 Map；无记录时返回 null
     */
    Map<String, BigDecimal> sumSummaryByUserId(@Param("userId") Long userId);
}
