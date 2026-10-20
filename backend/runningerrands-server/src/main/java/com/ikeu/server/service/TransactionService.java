package com.ikeu.server.service;

import com.ikeu.common.result.PageResult;
import com.ikeu.model.entity.TransactionRecord;
import com.ikeu.model.vo.TransactionVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 资金流水服务接口，提供流水记录、用户查询和管理员查询等功能。
 * @author ikeu
 * @since 2026/05/22
 */
public interface TransactionService {

    /**
     * 记录一笔资金流水（收支明细），持久化交易记录并更新用户余额快照。
     *
     * @param userId 用户ID
     * @param taskId 关联任务ID（可为null，表示非任务产生的流水如充值/提现）
     * @param amount 交易金额（正数为收入，负数为支出）
     * @param type 交易类型（1-支出 2-收入 3-充值 4-提现 5-退款）
     * @param balanceBefore 交易前余额
     * @param balanceAfter 交易后余额
     */
    void recordTransaction(Long userId, Long taskId, BigDecimal amount, Integer type,
                           BigDecimal balanceBefore, BigDecimal balanceAfter);

    /**
     * 用户分页查询自己的资金流水，支持按类型和时间范围筛选。
     *
     * <p>按 userId 精确匹配，可选按 type、时间区间筛选，按交易时间倒序返回。
     *
     * @param userId 用户ID
     * @param type 交易类型（可选，null 表示查询全部）
     * @param start 开始时间（可选）
     * @param end 结束时间（可选）
     * @param page 页码
     * @param size 每页条数
     * @return 流水分页结果
     */
    PageResult<TransactionVO> listUserTransactions(Long userId, Integer type, LocalDateTime start, LocalDateTime end,
                                                   int page, int size);

    /**
     * 管理员分页查询所有用户的资金流水，支持按类型、用户和时间范围筛选。
     *
     * <p>查询后批量加载用户信息，填充 nickname 字段到 TransactionVO 中。
     *
     * @param type 交易类型（可选，null 表示查询全部）
     * @param userId 用户ID（可选，null 表示全部用户）
     * @param start 开始时间（可选）
     * @param end 结束时间（可选）
     * @param page 页码
     * @param size 每页条数
     * @return 流水分页结果（含用户昵称）
     */
    PageResult<TransactionVO> listAllTransactions(Integer type, Long userId, LocalDateTime start, LocalDateTime end, int page, int size);

    /**
     * 查询用户全量流水汇总（总收入、总支出），不受分页限制。
     *
     * @param userId 用户ID
     * @return 用户流水汇总 Map，键为 total_income（总收入）和 total_expense（总支出）
     */
    Map<String, BigDecimal> getSummary(Long userId);
}
