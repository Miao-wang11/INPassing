package com.ikeu.server.aspect;

import com.ikeu.model.entity.Task;
import com.ikeu.model.entity.TaskOrder;
import com.ikeu.server.annotation.SendNotification;
import com.ikeu.server.mapper.TaskMapper;
import com.ikeu.server.mapper.TaskOrderMapper;
import com.ikeu.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知发送切面，拦截标注 {@code @SendNotification} 的方法，自动创建并推送站内通知。
 * @author ikeu
 * @since 2026/05/14
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class NotificationAspect {

    private final NotificationService notificationService;
    private final TaskMapper taskMapper;
    private final TaskOrderMapper taskOrderMapper;

    /**
     * 后置通知，在方法执行成功后根据 {@code @SendNotification} 注解配置自动发送通知。
     *
     * <p>实现逻辑：
     * <ol>
     *   <li>根据 targetUserType 解析接收者ID —— 1=发布者（通过 taskId 或 orderId 反查任务获取 publisherId），
     *       2=跑腿员（通过 orderId 反查订单获取 runnerId）</li>
     *   <li>从方法参数中提取变量（参数名→参数值映射），并额外处理 taskNo 占位符：
     *       通过 taskId 或 orderId 反查任务编号</li>
     *   <li>将通知模板中的 {@code #参数名} 占位符替换为实际参数值</li>
     *   <li>调用 NotificationService 持久化并推送通知</li>
     * </ol>
     * 整个过程包裹在 try-catch 中，异常仅记录 error 日志不影响业务方法执行。
     *
     * @param joinPoint 连接点
     * @param ret 方法返回值
     * @param sendNotification 发送通知注解实例
     */
    @AfterReturning(pointcut = "@annotation(sendNotification)", returning = "ret")
    public void send(JoinPoint joinPoint, Object ret, SendNotification sendNotification) {
        try {
            Long receiverId = resolveReceiverId(joinPoint, sendNotification.targetUserType());
            if (receiverId == null) return;

            String title = sendNotification.title();
            String content = sendNotification.content();

            Map<String, Object> vars = getMethodVariables(joinPoint);
            String taskNo = null;
            if (vars.containsKey("taskId")) {
                Long taskId = (Long) vars.get("taskId");
                Task task = taskMapper.selectById(taskId);
                if (task != null) taskNo = task.getTaskNo();
            } else if (vars.containsKey("orderId")) {
                Long orderId = (Long) vars.get("orderId");
                TaskOrder order = taskOrderMapper.selectById(orderId);
                if (order != null) {
                    Task task = taskMapper.selectById(order.getTaskId());
                    if (task != null) taskNo = task.getTaskNo();
                }
            }
            if (taskNo != null) {
                vars.put("taskNo", taskNo);
            }

            title = replacePlaceholders(title, vars);
            content = replacePlaceholders(content, vars);

            Long targetId = getTargetId(vars);
            notificationService.sendNotification(receiverId, sendNotification.noticeType(), title, content, targetId);
        } catch (Exception e) {
            log.error("发送通知失败，切点: {}, 注解: {}", joinPoint.getSignature(), sendNotification, e);
        }
    }

    /**
     * 根据 targetUserType 从方法参数中解析通知接收者ID。
     *
     * <p>通过反射获取方法参数名列表，按参数名定位 taskId 或 orderId：
     * targetUserType=1（发布者）→ 通过 taskId 或 orderId 反查 task 表获取 publisherId；
     * targetUserType=2（跑腿员）→ 通过 orderId 反查 task_order 表获取 runnerId。
     *
     * @param joinPoint 连接点
     * @param targetUserType 目标用户类型（1-发布者，2-跑腿员）
     * @return 接收者用户ID，无法确定时返回 null
     */
    private Long resolveReceiverId(JoinPoint joinPoint, int targetUserType) {
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();

        if (paramNames == null) return null;

        Long orderId = null;
        Long taskId = null;

        for (int i = 0; i < paramNames.length; i++) {
            if ("orderId".equals(paramNames[i]) && args[i] instanceof Long) {
                orderId = (Long) args[i];
            } else if ("taskId".equals(paramNames[i]) && args[i] instanceof Long) {
                taskId = (Long) args[i];
            }
        }

        if (targetUserType == 1) {
            if (taskId != null) {
                Task task = taskMapper.selectById(taskId);
                return task != null ? task.getPublisherId() : null;
            }
            if (orderId != null) {
                TaskOrder order = taskOrderMapper.selectById(orderId);
                if (order != null) {
                    Task task = taskMapper.selectById(order.getTaskId());
                    return task != null ? task.getPublisherId() : null;
                }
            }
        } else if (targetUserType == 2) {
            if (orderId != null) {
                TaskOrder order = taskOrderMapper.selectById(orderId);
                return order != null ? order.getRunnerId() : null;
            }
        }
        return null;
    }

    /**
     * 通过反射获取目标方法的参数名和参数值映射。
     *
     * @param joinPoint 连接点
     * @return 参数名→参数值的映射
     */
    private Map<String, Object> getMethodVariables(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        Map<String, Object> vars = new HashMap<>();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                vars.put(paramNames[i], args[i]);
            }
        }
        return vars;
    }

    /**
     * 替换通知模板中的 {@code #参数名} 占位符为实际参数值。
     *
     * @param template 通知模板字符串
     * @param vars 变量映射
     * @return 替换后的字符串
     */
    private String replacePlaceholders(String template, Map<String, Object> vars) {
        if (template == null) return null;
        String result = template;
        for (Map.Entry<String, Object> entry : vars.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace("#" + entry.getKey(), entry.getValue().toString());
            }
        }
        return result;
    }

    /**
     * 从变量映射中获取业务目标ID（优先 orderId，其次 taskId）。
     *
     * @param vars 变量映射
     * @return 目标ID
     */
    private Long getTargetId(Map<String, Object> vars) {
        if (vars.containsKey("orderId")) return (Long) vars.get("orderId");
        if (vars.containsKey("taskId")) return (Long) vars.get("taskId");
        return null;
    }
}
