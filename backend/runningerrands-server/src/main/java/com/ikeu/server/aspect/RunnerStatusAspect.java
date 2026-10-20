package com.ikeu.server.aspect;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ikeu.common.constant.StatusConstant;
import com.ikeu.model.entity.RunnerProfile;
import com.ikeu.model.vo.UserLoginVO;
import com.ikeu.server.mapper.RunnerProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 配送员状态切面，处理登录自动上线和退出登录自动离线逻辑。
 * @author ikeu
 * @since 2026/05/16
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RunnerStatusAspect {

    private final RunnerProfileMapper runnerProfileMapper;

    /**
     * 账号密码登录成功后，若当前用户为已认证配送员且处于离线状态，自动上线。
     *
     * <p>校验逻辑：检查该用户是否存在 runner_profile 记录、认证状态是否为已通过、
     * 当前是否已离线 —— 全部满足则设置 is_online=在线并更新 updated_at。
     * 整个过程包裹在 try-catch 中，异常仅记录日志不影响登录主流程。
     *
     * @param ret 登录返回的 UserLoginVO
     */
    @AfterReturning(value = "execution(* com.ikeu.server.service.UserService.login(..))", returning = "ret")
    public void afterLogin(UserLoginVO ret) {
        if (ret == null || ret.getUserId() == null) return;
        trySetOnline(ret.getUserId());
    }

    /**
     * 微信登录成功后，自动上线逻辑（同 afterLogin）。
     *
     * @param ret 登录返回的 UserLoginVO
     */
    @AfterReturning(value = "execution(* com.ikeu.server.service.UserService.weChatLogin(..))", returning = "ret")
    public void afterWeChatLogin(UserLoginVO ret) {
        if (ret == null || ret.getUserId() == null) return;
        trySetOnline(ret.getUserId());
    }

    /**
     * 退出登录后，若当前用户为配送员且在线，自动离线。
     *
     * <p>校验逻辑：检查该用户是否存在 runner_profile 记录、当前是否在线 ——
     * 全部满足则设置 is_online=离线并更新 updated_at。
     * 整个过程包裹在 try-catch 中，异常仅记录日志不影响退出登录主流程。
     *
     * @param userId 用户ID（由切点参数绑定）
     */
    @AfterReturning(value = "execution(* com.ikeu.server.service.UserService.logout(..)) && args(userId)")
    public void afterLogout(Long userId) {
        if (userId == null) return;
        trySetOffline(userId);
    }

    /**
     * 尝试将配送员设为在线状态。
     *
     * <p>依次校验：runner_profile 存在 → 认证状态为已通过 → 当前状态为离线，
     * 全部通过后更新 is_online 为在线。
     */
    private void trySetOnline(Long userId) {
        try {
            RunnerProfile profile = runnerProfileMapper.selectOne(
                    new LambdaQueryWrapper<RunnerProfile>().eq(RunnerProfile::getUserId, userId));
            if (profile == null) return;
            if (!Objects.equals(profile.getVerifyStatus(), StatusConstant.CERTIFY_APPROVED)) return;
            if (Objects.equals(profile.getIsOnline(), StatusConstant.RUNNER_ONLINE)) return;
            profile.setIsOnline(StatusConstant.RUNNER_ONLINE);
            profile.setUpdatedAt(LocalDateTime.now());
            runnerProfileMapper.updateById(profile);
            log.info("配送员 {} 登录后自动上线", userId);
        } catch (Exception e) {
            log.error("配送员登录自动上线失败: userId={}", userId, e);
        }
    }

    /**
     * 尝试将配送员设为离线状态。
     *
     * <p>依次校验：runner_profile 存在 → 当前状态为在线，
     * 全部通过后更新 is_online 为离线。
     */
    private void trySetOffline(Long userId) {
        try {
            RunnerProfile profile = runnerProfileMapper.selectOne(
                    new LambdaQueryWrapper<RunnerProfile>().eq(RunnerProfile::getUserId, userId));
            if (profile == null) return;
            if (Objects.equals(profile.getIsOnline(), StatusConstant.RUNNER_OFFLINE)) return;
            profile.setIsOnline(StatusConstant.RUNNER_OFFLINE);
            profile.setUpdatedAt(LocalDateTime.now());
            runnerProfileMapper.updateById(profile);
            log.info("配送员 {} 退出登录后自动离线", userId);
        } catch (Exception e) {
            log.error("配送员退出登录自动离线失败: userId={}", userId, e);
        }
    }
}
