package com.ikeu.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikeu.common.result.PageResult;
import com.ikeu.model.entity.TransactionRecord;
import com.ikeu.model.entity.User;

import java.util.Map;
import com.ikeu.model.vo.TransactionVO;
import com.ikeu.server.mapper.TransactionRecordMapper;
import com.ikeu.server.mapper.UserMapper;
import com.ikeu.server.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 交易流水服务实现，处理资金流水的记录、用户查询和管理员查询。
 * @author ikeu
 * @since 2026/05/22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl extends ServiceImpl<TransactionRecordMapper, TransactionRecord>
        implements TransactionService {

    private final TransactionRecordMapper transactionRecordMapper;
    private final UserMapper userMapper;

    /** 将实体转换为 VO，填充所有字段。 */
    private TransactionVO toVO(TransactionRecord r) {
        return TransactionVO.builder()
                .id(r.getId()).userId(r.getUserId()).taskId(r.getTaskId())
                .amount(r.getAmount()).type(r.getType())
                .balanceBefore(r.getBalanceBefore()).balanceAfter(r.getBalanceAfter())
                .createdAt(r.getCreatedAt()).build();
    }

    /**
     * 记录一笔资金流水，构建 TransactionRecord 实体并持久化。
     *
     * @param userId 用户ID
     * @param taskId 关联任务ID（可为null）
     * @param amount 交易金额（正数为收入，负数为支出）
     * @param type 交易类型
     * @param balanceBefore 交易前余额
     * @param balanceAfter 交易后余额
     */
    @Override
    @Transactional
    public void recordTransaction(Long userId, Long taskId, BigDecimal amount, Integer type,
                                  BigDecimal balanceBefore, BigDecimal balanceAfter) {
        TransactionRecord record = TransactionRecord.builder()
                .userId(userId).taskId(taskId).amount(amount).type(type)
                .balanceBefore(balanceBefore).balanceAfter(balanceAfter)
                .createdAt(LocalDateTime.now()).build();
        save(record);
    }

    /**
     * 用户查询自己的资金流水，支持按类型和时间范围筛选，按创建时间倒序分页。
     *
     * @param userId 用户ID
     * @param type 交易类型（可选）
     * @param start 开始时间（可选）
     * @param end 结束时间（可选）
     * @param page 页码
     * @param size 每页条数
     * @return 分页流水列表
     */
    @Override
    public PageResult<TransactionVO> listUserTransactions(Long userId, Integer type,
                                                          LocalDateTime start, LocalDateTime end,
                                                          int page, int size) {
        LambdaQueryWrapper<TransactionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TransactionRecord::getUserId, userId)
                .eq(type != null, TransactionRecord::getType, type)
                .ge(start != null, TransactionRecord::getCreatedAt, start)
                .le(end != null, TransactionRecord::getCreatedAt, end)
                .orderByDesc(TransactionRecord::getCreatedAt);
        Page<TransactionRecord> p = page(new Page<>(page, size), wrapper);
        List<TransactionVO> records = p.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(p.getTotal(), records);
    }

    /**
     * 管理员查询所有用户的资金流水，支持按类型、用户ID和时间范围筛选。
     *
     * <p>查询后批量加载用户信息填充 nickname 字段到 TransactionVO 中。
     *
     * @param type 交易类型（可选）
     * @param userId 用户ID（可选）
     * @param start 开始时间（可选）
     * @param end 结束时间（可选）
     * @param page 页码
     * @param size 每页条数
     * @return 分页流水列表（含用户昵称）
     */
    @Override
    public PageResult<TransactionVO> listAllTransactions(Integer type, Long userId,
                                                      LocalDateTime start, LocalDateTime end,
                                                      int page, int size) {
        LambdaQueryWrapper<TransactionRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(type != null, TransactionRecord::getType, type)
                .eq(userId != null, TransactionRecord::getUserId, userId)
                .ge(start != null, TransactionRecord::getCreatedAt, start)
                .le(end != null, TransactionRecord::getCreatedAt, end)
                .orderByDesc(TransactionRecord::getCreatedAt);

        Page<TransactionRecord> p = transactionRecordMapper.selectPage(new Page<>(page, size), wrapper);
        if (p.getRecords().isEmpty()) return new PageResult<>(0L, Collections.emptyList());

        List<Long> userIds = p.getRecords().stream().map(TransactionRecord::getUserId).distinct().collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        List<TransactionVO> records = p.getRecords().stream().map(tr -> {
            User u = userMap.get(tr.getUserId());
            return TransactionVO.builder()
                    .id(tr.getId()).userId(tr.getUserId())
                    .userNickname(u != null ? u.getNickname() : "")
                    .taskId(tr.getTaskId()).amount(tr.getAmount()).type(tr.getType())
                    .balanceBefore(tr.getBalanceBefore()).balanceAfter(tr.getBalanceAfter())
                    .createdAt(tr.getCreatedAt()).build();
        }).collect(Collectors.toList());

        return new PageResult<>(p.getTotal(), records);
    }

    /**
     * 查询用户全量流水汇总，委托 Mapper 执行聚合查询。
     *
     * <p>当 Mapper 返回 null 时（无流水记录），默认返回零值 Map。
     * 非 null 结果确保 total_income 和 total_expense 键存在且不为空。
     *
     * @param userId 用户ID
     * @return 用户流水汇总 Map，键为 total_income（总收入）和 total_expense（总支出）
     */
    @Override
    public Map<String, BigDecimal> getSummary(Long userId) {
        Map<String, BigDecimal> result = transactionRecordMapper.sumSummaryByUserId(userId);
        if (result == null) {
            return Map.of("total_income", BigDecimal.ZERO, "total_expense", BigDecimal.ZERO);
        }
        result.putIfAbsent("total_income", BigDecimal.ZERO);
        result.putIfAbsent("total_expense", BigDecimal.ZERO);
        return result;
    }
}
