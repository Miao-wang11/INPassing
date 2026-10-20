package com.ikeu.server.aspect;

import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.context.BaseContext;
import com.ikeu.common.exception.ForbiddenException;
import com.ikeu.model.entity.Admin;
import com.ikeu.server.annotation.RequireRole;
import com.ikeu.server.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 角色权限检查切面，拦截标注 @RequireRole 的方法，校验当前管理员角色。
 * @author ikeu
 * @since 2026/05/31
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RoleCheckAspect {

    private final AdminMapper adminMapper;

    /**
     * 前置通知：在标注 {@link RequireRole @RequireRole} 的方法执行前校验当前管理员的角色权限。
     *
     * <p>从 {@link BaseContext} 获取当前登录管理员 ID，查询数据库获取其角色值，
     * 与注解中指定的角色数组逐一匹配。若当前角色不在允许范围内，抛出 {@link ForbiddenException}。
     * 若管理员未登录或账号不存在，同样抛出 {@link ForbiddenException}。
     *
     * @param joinPoint   切入点，用于记录被拦截的方法签名日志
     * @param requireRole 方法上标注的 {@link RequireRole} 注解实例，包含允许的角色值列表
     * @throws ForbiddenException 当管理员未登录、账号不存在或角色无权限时抛出
     */
    @Before("@annotation(requireRole)")
    public void checkRole(JoinPoint joinPoint, RequireRole requireRole) {
        Long adminId = BaseContext.getCurrentId();
        if (adminId == null) {
            throw new ForbiddenException(MessageConstant.ACCESS_DENIED);
        }

        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new ForbiddenException(MessageConstant.ACCESS_DENIED);
        }

        int currentRole = admin.getRole();
        boolean hasPermission = Arrays.stream(requireRole.value()).anyMatch(r -> r == currentRole);
        if (!hasPermission) {
            log.warn("管理员 {} (角色:{}) 尝试越权访问 {}", admin.getUsername(), currentRole, joinPoint.getSignature());
            throw new ForbiddenException(MessageConstant.ACCESS_DENIED);
        }
    }
}
