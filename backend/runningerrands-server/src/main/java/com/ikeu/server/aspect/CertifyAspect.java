package com.ikeu.server.aspect;

import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.constant.StatusConstant;
import com.ikeu.common.context.BaseContext;
import com.ikeu.common.exception.BusinessException;
import com.ikeu.model.entity.User;
import com.ikeu.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 实名认证切面，拦截标注 {@code @RequireCertify} 的方法，校验用户是否已完成身份认证。
 * @author ikeu
 * @since 2026/05/14
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class CertifyAspect {

    private final UserMapper userMapper;

    /**
     * 前置通知，检查当前登录用户是否已完成实名认证。
     *
     * <p>校验逻辑：
     * <ol>
     *   <li>从 {@link BaseContext} ThreadLocal 获取当前用户ID —— 为 null 则未登录，抛出 USER_NOT_LOGIN</li>
     *   <li>查询 user 表校验用户是否存在 —— 不存在则抛出 USER_NOT_EXIST</li>
     *   <li>校验 is_certify 字段是否等于已认证通过状态 —— 未通过则抛出 USER_NOT_CERTIFIED</li>
     * </ol>
     * 通过所有校验后放行，让请求继续执行被拦截的方法。
     */
    @Before("@annotation(com.ikeu.server.annotation.RequireCertify)")
    public void checkCertify() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new BusinessException(MessageConstant.USER_NOT_LOGIN);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(MessageConstant.USER_NOT_EXIST);
        }
        if (!Objects.equals(user.getIsCertify(), StatusConstant.CERTIFY_APPROVED)) {
            throw new BusinessException(MessageConstant.USER_NOT_CERTIFIED);
        }
    }
}
