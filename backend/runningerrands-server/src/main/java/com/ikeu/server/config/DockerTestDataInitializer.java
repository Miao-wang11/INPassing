package com.ikeu.server.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ikeu.common.constant.StatusConstant;
import com.ikeu.model.entity.RunnerProfile;
import com.ikeu.model.entity.User;
import com.ikeu.server.mapper.RunnerProfileMapper;
import com.ikeu.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Docker 环境测试数据初始化器，仅在 docker profile 激活时运行。
 * @author ikeu
 * @since 2026/06/16
 */
@Slf4j
@Component
@Profile("docker")
@RequiredArgsConstructor
public class DockerTestDataInitializer implements ApplicationRunner {

    private static final String TEST_PASSWORD = "123456";

    private final UserMapper userMapper;
    private final RunnerProfileMapper runnerProfileMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        initTestUser("testuser", "13800000001", "测试用户", true, true);
        initTestUser("testuser2", "13800000002", "测试用户2", true, false);
        log.info("Docker 测试数据初始化完成");
    }

    private void initTestUser(String username, String phone, String nickname,
                               boolean setPayPassword, boolean applyRunner) {
        boolean exists = userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (exists) {
            log.info("测试用户 {} 已存在，跳过", username);
            return;
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(TEST_PASSWORD))
                .phone(phone)
                .nickname(nickname)
                .avatarUrl("/api/imgs/default_avatar.jpg")
                .balance(BigDecimal.valueOf(100.00))
                .status(StatusConstant.ENABLE)
                .isCertify(StatusConstant.CERTIFY_APPROVED)
                .sex("男")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (setPayPassword) {
            user.setPayPassword(passwordEncoder.encode(TEST_PASSWORD));
        }

        userMapper.insert(user);
        log.info("已创建测试用户: {} (phone={}, payPwd={})", username, phone, setPayPassword);

        if (applyRunner) {
            RunnerProfile profile = RunnerProfile.builder()
                    .userId(user.getId())
                    .realName(nickname)
                    .verifyStatus(StatusConstant.CERTIFY_APPROVED)
                    .creditScore(100)
                    .totalOrders(0)
                    .successOrders(0)
                    .avgRating(BigDecimal.valueOf(5.0))
                    .isOnline(StatusConstant.RUNNER_OFFLINE)
                    .maxConcurrentOrders(3)
                    .currentOrders(0)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            runnerProfileMapper.insert(profile);
            log.info("已为 {} 创建跑腿员档案", username);
        }
    }
}
