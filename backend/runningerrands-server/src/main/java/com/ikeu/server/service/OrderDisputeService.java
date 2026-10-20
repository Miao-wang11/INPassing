package com.ikeu.server.service;

import cn.hutool.json.JSONUtil;
import com.ikeu.common.constant.RedisConstant;
import com.ikeu.common.constant.StatusConstant;
import com.ikeu.common.context.BaseContext;
import com.ikeu.common.exception.BusinessException;
import com.ikeu.common.exception.ForbiddenException;
import com.ikeu.common.result.PageResult;
import com.ikeu.model.entity.Task;
import com.ikeu.model.entity.TaskOrder;
import com.ikeu.server.mapper.RunnerProfileMapper;
import com.ikeu.server.mapper.TaskMapper;
import com.ikeu.server.mapper.TaskOrderMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Simulated full-refund dispute arbitration BEFORE settlement.
 * Only the WAIT_CONFIRM phase is disputable in this first release.
 * Lock order is identical to completion: Redis task lock, then MySQL writes.
 * The case row and two audit entries are persisted in the same transaction
 * as the payment ledger and task/order status update.
 */
@Service
@RequiredArgsConstructor
public class OrderDisputeService {
    private final JdbcTemplate jdbc;
    private final TaskOrderMapper orderMapper;
    private final TaskMapper taskMapper;
    private final PaymentService payments;
    private final RunnerProfileMapper runners;
    private final RedissonClient redisson;
    private final CacheManager caches;

    public record ApplyForm(String reason, List<String> evidenceUrls) {}
    public record DecisionForm(String decision, String note) {}
    public record CaseView(Long id,Long orderId,Long taskId,Long applicantId,
                           String reason,List<String> evidenceUrls,int status,
                           String resolutionNote,Long decidedBy,LocalDateTime decidedAt,
                           LocalDateTime createdAt) {}
    public record CaseEvent(Long id,String actorType,Long actorId,String eventType,
                            String detail,LocalDateTime createdAt) {}

    private static Long optionalId(ResultSet rs, String column) throws SQLException {
        Object value=rs.getObject(column);
        return value == null ? null : ((Number)value).longValue();
    }
    private static CaseView map(ResultSet rs,int n) throws SQLException {
        String json=rs.getString("evidence_json");
        List<String> evidence=json == null ? List.of() : JSONUtil.toList(json,String.class);
        return new CaseView(rs.getLong("id"),rs.getLong("order_id"),rs.getLong("task_id"),
                rs.getLong("applicant_id"),rs.getString("reason"),evidence,rs.getInt("status"),
                rs.getString("resolution_note"),optionalId(rs,"decided_by"),
                rs.getObject("decided_at")==null ? null : rs.getObject("decided_at",LocalDateTime.class),
                rs.getObject("created_at",LocalDateTime.class));
    }
    private static final String FIELDS="SELECT id,order_id,task_id,applicant_id,reason,evidence_json,"+
            "status,resolution_note,decided_by,decided_at,created_at FROM order_dispute ";

    private static String validText(String text,int max,String message) {
        if(text==null || text.isBlank() || text.trim().length()>max)throw new BusinessException(message);
        return text.trim();
    }
    private static void validateEvidence(List<String> urls) {
        if(urls==null)return;
        if(urls.size()>5)throw new BusinessException("凭证最多五张");
        for(String url:urls) {
            if(url==null || url.length()>500 || !url.startsWith("https://")
                    || url.contains(" ") || url.contains("\n"))throw new BusinessException("凭证链接无效");
        }
    }
    private static String safeJson(List<String> urls) {
        return JSONUtil.toJsonStr(urls==null?List.of():urls);
    }
    private void event(Long caseId,String type,Long actor,String operation,String detail) {
        jdbc.update("INSERT INTO dispute_event (dispute_id,actor_type,actor_id,event_type,detail) VALUES (?,?,?,?,?)",
                caseId,type,actor,operation,detail);
    }

    public boolean isOpen(Long orderId) {
        // Locking/current read avoids the REPEATABLE_READ snapshot established
        // before a competing dispute transaction acquired the task lock.
        List<Integer> states=jdbc.query("SELECT status FROM order_dispute WHERE order_id=? FOR UPDATE",
                (rs,i)->rs.getInt(1),orderId);
        return !states.isEmpty() && states.get(0)==1;
    }
    public void requireNoOpenCase(Long orderId) {
        if(isOpen(orderId))throw new BusinessException("该订单存在未处理的售后争议，暂不能确认完成或强制结算");
    }

    private <T> T locked(Long taskId, java.util.concurrent.Callable<T> action) {
        RLock lock=redisson.getLock(RedisConstant.ORDER_LOCK_KEY+taskId);
        try {
            if(!lock.tryLock(RedisConstant.LOCK_WAIT_TIME,RedisConstant.LOCK_EXPIRE,TimeUnit.SECONDS))
                throw new BusinessException("订单操作繁忙，请重试");
            return action.call();
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("订单操作被中断");
        } catch(RuntimeException ex) {
            throw ex;
        } catch(Exception ex) {
            throw new IllegalStateException("无法处理订单售后",ex);
        } finally {
            if(lock.isHeldByCurrentThread())lock.unlock();
        }
    }

    /**
     * Exactly one application per order; repeated POST does not overwrite old
     * evidence or restart arbitration. Held only while WAIT_CONFIRM.
     */
    @Transactional
    public CaseView apply(Long userId,Long orderId,ApplyForm form) {
        if(form==null)throw new BusinessException("请填写售后原因");
        String reason=validText(form.reason(),500,"售后原因必须为1到500字");
        validateEvidence(form.evidenceUrls());
        TaskOrder order=orderMapper.selectById(orderId);
        if(order==null)throw new BusinessException("订单不存在");
        return locked(order.getTaskId(),()->{
            TaskOrder latest=orderMapper.selectById(orderId);
            Task task=taskMapper.selectById(order.getTaskId());
            if(latest==null || task==null)throw new BusinessException("订单不存在");
            if(!Objects.equals(userId,task.getPublisherId())
                    && !Objects.equals(userId,latest.getRunnerId())) {
                throw new ForbiddenException("只有当前交易双方可以申请售后");
            }
            // Current-read the underlying order row under the same lock.
            // This waits for any previous completion transaction to commit.
            Integer committedStatus=jdbc.queryForObject(
                    "SELECT status FROM task_order WHERE id=? FOR UPDATE",Integer.class,orderId);
            if(!Objects.equals(committedStatus,StatusConstant.ORDER_WAIT_CONFIRM)) {
                throw new BusinessException("订单已结算或取消，不允许发起争议");
            }
            List<CaseView> old=jdbc.query(FIELDS+"WHERE order_id=?",OrderDisputeService::map,orderId);
            if(!old.isEmpty())return old.get(0);
            if(!Objects.equals(latest.getStatus(),StatusConstant.ORDER_WAIT_CONFIRM)
                    || !Objects.equals(task.getStatus(),StatusConstant.TASK_WAIT_CONFIRM)) {
                throw new BusinessException("仅已送达待确认的订单可发起争议");
            }
            jdbc.update("INSERT INTO order_dispute (order_id,task_id,applicant_id,reason,evidence_json,status)"+
                    " VALUES (?,?,?,?,?,1)",orderId,task.getId(),userId,reason,safeJson(form.evidenceUrls()));
            Long caseId=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            event(caseId,"USER",userId,"APPLY",reason);
            return getById(caseId);
        });
    }

    public CaseView getForParty(Long userId,Long id) {
        CaseView dispute=getById(id);
        TaskOrder order=orderMapper.selectById(dispute.orderId());
        Task task=taskMapper.selectById(dispute.taskId());
        if(order==null || task==null || (!Objects.equals(userId,task.getPublisherId())
                && !Objects.equals(userId,order.getRunnerId()))) {
            throw new ForbiddenException("不是此交易的参与方");
        }
        return dispute;
    }
    public CaseView getById(Long id) {
        List<CaseView> rows=jdbc.query(FIELDS+"WHERE id=?",OrderDisputeService::map,id);
        if(rows.isEmpty())throw new BusinessException("售后单不存在");
        return rows.get(0);
    }
    public List<CaseEvent> eventsForParty(Long userId,Long caseId) {
        getForParty(userId,caseId);
        return events(caseId);
    }
    public List<CaseEvent> events(Long caseId) {
        getById(caseId);
        return jdbc.query("SELECT id,actor_type,actor_id,event_type,detail,created_at FROM dispute_event "+
                "WHERE dispute_id=? ORDER BY id",
                (rs,i)->new CaseEvent(rs.getLong(1),rs.getString(2),rs.getLong(3),
                        rs.getString(4),rs.getString(5),rs.getObject(6,LocalDateTime.class)),caseId);
    }
    public List<CaseView> mine(Long userId) {
        return jdbc.query(FIELDS+"WHERE applicant_id=? ORDER BY id DESC LIMIT 100",
                OrderDisputeService::map,userId);
    }
    public PageResult<CaseView> adminCases(Integer status,int page,int size) {
        if(page<1||size<1||size>100||status!=null&&status!=1&&status!=2&&status!=3)
            throw new BusinessException("筛选条件无效");
        String where=status==null?"":" WHERE status=?";
        Object[] args=status==null?new Object[]{}:new Object[]{status};
        Long total=jdbc.queryForObject("SELECT COUNT(*) FROM order_dispute"+where,Long.class,args);
        Object[] pagination=status==null?new Object[]{size,(page-1)*size}:
                new Object[]{status,size,(page-1)*size};
        return new PageResult<>(total==null?0L:total,
                jdbc.query(FIELDS+where+" ORDER BY id DESC LIMIT ? OFFSET ?",
                        OrderDisputeService::map,pagination));
    }

    /**
     * REJECT: release the settlement hold, buyer can confirm normally.
     * REFUND: only if WAIT_CONFIRM and still prepaid; terminal CANCELLED and
     * FULL mock refund, with runner capacity release, all in one transaction.
     * This V1 does NOT support partially refunded/previously paid-out cases.
     */
    @Transactional
    public CaseView decide(Long adminId,Long caseId,DecisionForm request) {
        if(request==null || !("REJECT".equals(request.decision())||"REFUND".equals(request.decision())))
            throw new BusinessException("只能驳回争议或模拟全额退款");
        String note=validText(request.note(),500,"必须填写仲裁理由（最多500字）");
        CaseView before=getById(caseId);
        return locked(before.taskId(),()->{
            CaseView current=getById(caseId);
            if(current.status()!=1)throw new BusinessException("售后已处理，不能重复仲裁");
            TaskOrder order=orderMapper.selectById(current.orderId());
            Task task=taskMapper.selectById(current.taskId());
            if(order==null||task==null||!Objects.equals(order.getStatus(),StatusConstant.ORDER_WAIT_CONFIRM)
                    ||!Objects.equals(task.getStatus(),StatusConstant.TASK_WAIT_CONFIRM)) {
                throw new BusinessException("订单状态已变化，需人工核对资金");
            }
            int next="REFUND".equals(request.decision())?3:2;
            if(next==3) {
                // A dispute resolution is the ONLY authorised transition
                // WAIT_CONFIRM -> CANCELLED; ordinary OrderStateMachine stays strict.
                int changed=jdbc.update("UPDATE task_order SET status=5,cancel_time=NOW(),cancel_reason=? "+
                                "WHERE id=? AND status=3","售后仲裁全额模拟退款",order.getId());
                int tchanged=jdbc.update("UPDATE task SET status=6,updated_at=NOW() "+
                                "WHERE id=? AND status=4",task.getId());
                if(changed!=1||tchanged!=1)throw new BusinessException("订单并发修改，仲裁未生效");
                payments.refundForTask(task.getPublisherId(),task.getId(),task.getReward());
                runners.decrementCurrentOrders(order.getRunnerId());
            }
            int updated=jdbc.update("UPDATE order_dispute SET status=?,resolution_note=?,decided_by=?,decided_at=NOW() "+
                            "WHERE id=? AND status=1",next,note,adminId,caseId);
            if(updated!=1)throw new BusinessException("重复仲裁已被拒绝");
            event(caseId,"ADMIN",adminId,next==3?"FULL_REFUND":"REJECT",note);
            return getById(caseId);
        });
    }
}
