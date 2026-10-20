package com.ikeu.server.service;

import com.ikeu.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Read-only operational ledger diagnostics backed by actual MySQL records.
 * No estimated revenue and no replay/rebuild of mutable wallet balances.
 */
@Service
@RequiredArgsConstructor
public class OperationsAuditService {
    private final JdbcTemplate jdbc;

    public record Summary(long pendingCases,long rejectedCases,long refundedCases,BigDecimal totalDisputeRefunds) {}
    public record LedgerCheck(long taskId,int taskStatus,BigDecimal expected,
                              BigDecimal charged,BigDecimal refunded,BigDecimal disbursed,
                              long debitRows,long refundRows,boolean consistent,String issue) {}

    private static final String LEDGER_SQL = """
        SELECT t.id AS task_id,t.status,t.reward,
          COALESCE(tr.paid,0) paid,COALESCE(tr.refunded,0) refunded,
          COALESCE(tr.disbursed,0) disbursed,
          COALESCE(tr.debits,0) debits,COALESCE(tr.refunds,0) refunds
        FROM task t
        LEFT JOIN (
          SELECT task_id,
            SUM(CASE WHEN type=1 THEN -amount ELSE 0 END) AS paid,
            SUM(CASE WHEN type=5 THEN amount ELSE 0 END) AS refunded,
            SUM(CASE WHEN type IN (2,6) THEN amount ELSE 0 END) AS disbursed,
            SUM(CASE WHEN type=1 THEN 1 ELSE 0 END) AS debits,
            SUM(CASE WHEN type=5 THEN 1 ELSE 0 END) AS refunds
          FROM transaction_record WHERE task_id IS NOT NULL GROUP BY task_id
        ) tr ON tr.task_id=t.id
        """;

    private static LedgerCheck map(ResultSet rs,int row) throws SQLException {
        BigDecimal expected=rs.getBigDecimal("reward");
        BigDecimal charged=rs.getBigDecimal("paid");
        BigDecimal refunded=rs.getBigDecimal("refunded");
        BigDecimal disbursed=rs.getBigDecimal("disbursed");
        long debitRows=rs.getLong("debits"),refundRows=rs.getLong("refunds");
        int status=rs.getInt("status");
        String problem="";
        if(charged.compareTo(expected)!=0||debitRows!=1) {
            problem="预付总额或扣款次数异常";
        }else if(status==5 && (disbursed.compareTo(expected)!=0 || refunded.signum()!=0)) {
            problem="已完成订单的结算/退款金额异常";
        }else if(status==6 && (refunded.compareTo(expected)!=0||disbursed.signum()!=0||refundRows!=1)) {
            problem="已取消订单的退款/结算金额异常";
        }else if(status>=1&&status<=4 && (disbursed.signum()!=0 || refunded.signum()!=0)) {
            problem="未完成订单发生了提前结算或退款";
        }
        return new LedgerCheck(rs.getLong("task_id"),status,expected,charged,refunded,
                disbursed,debitRows,refundRows,problem.isEmpty(),problem);
    }

    public Summary summary() {
        var numbers=jdbc.queryForMap("""
            SELECT COUNT(*) total, COALESCE(SUM(status=1),0) opened,
            COALESCE(SUM(status=2),0) rejected, COALESCE(SUM(status=3),0) refunded
            FROM order_dispute
            """);
        // Calculate actual wallet refunds associated with adjudicated cases,
        // rather than summing the original task price without ledger evidence.
        BigDecimal total=jdbc.queryForObject("""
            SELECT COALESCE(SUM(tr.amount),0)
            FROM order_dispute d JOIN transaction_record tr ON tr.task_id=d.task_id AND tr.type=5
            WHERE d.status=3
            """,BigDecimal.class);
        return new Summary(((Number)numbers.get("opened")).longValue(),
                ((Number)numbers.get("rejected")).longValue(),
                ((Number)numbers.get("refunded")).longValue(),total);
    }

    public LedgerCheck inspect(Long taskId) {
        if(taskId==null||taskId<1)throw new BusinessException("任务ID无效");
        List<LedgerCheck> rows=jdbc.query(LEDGER_SQL+" WHERE t.id=?",
                OperationsAuditService::map,taskId);
        if(rows.isEmpty())throw new BusinessException("任务不存在");
        return rows.get(0);
    }

    /** Diagnostics are bounded to the most recent 200 anomalies by task ID. */
    public List<LedgerCheck> suspicious(int limit) {
        if(limit<1||limit>200)throw new BusinessException("最多查询200条");
        // Return only anomalous rows. A subquery avoids post-filtering 200
        // valid tasks and missing a later anomalous task.
        String derived=LEDGER_SQL;
        String sql="SELECT * FROM ("+derived+") v WHERE " +
                "v.paid <> v.reward OR v.debits <> 1 " +
                "OR (v.status=5 AND (v.disbursed <> v.reward OR v.refunded<>0)) " +
                "OR (v.status=6 AND (v.refunded <> v.reward OR v.disbursed<>0 OR v.refunds<>1)) " +
                "OR (v.status BETWEEN 1 AND 4 AND (v.refunded<>0 OR v.disbursed<>0)) " +
                "ORDER BY v.task_id DESC LIMIT ?";
        return jdbc.query(sql,OperationsAuditService::map,limit);
    }
}
