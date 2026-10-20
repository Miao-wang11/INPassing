package com.ikeu.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.constant.StatusConstant;
import com.ikeu.common.exception.ForbiddenException;
import com.ikeu.common.exception.NotFoundException;
import com.ikeu.model.entity.RunnerProfile;
import com.ikeu.model.entity.User;
import com.ikeu.server.mapper.RunnerProfileMapper;
import com.ikeu.server.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * No buyer/seller account split: the same student ID can publish and accept.
 * runner_profile is an auxiliary capacity/credit record, created lazily when
 * an already verified student explicitly clicks "accept task".
 */
@Service
@RequiredArgsConstructor
public class StudentAcceptanceProfileService {
    private final UserMapper userMapper;
    private final RunnerProfileMapper runnerProfileMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public RunnerProfile ensureForAccept(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException(MessageConstant.USER_NOT_EXIST);
        }
        if (!Objects.equals(user.getStatus(), StatusConstant.ENABLE)) {
            throw new ForbiddenException(MessageConstant.USER_DISABLED);
        }
        if (!Objects.equals(user.getIsCertify(), StatusConstant.CERTIFY_APPROVED)) {
            throw new ForbiddenException(MessageConstant.USER_NOT_CERTIFIED);
        }

        // Existing restrictions must survive a repeat accept attempt.
        RunnerProfile profile = findByUserId(userId);
        if (profile != null) {
            return profile;
        }

        // The MySQL uk_user_id constraint protects concurrent first accepts.
        // The insert is conditional on current user status/certification,
        // and the duplicate path never modifies any existing profile fields.
        runnerProfileMapper.createForCertifiedStudentIfAbsent(userId);
        profile = findByUserId(userId);
        if (profile == null) {
            // Certification could have been revoked between reads.
            throw new ForbiddenException(MessageConstant.USER_NOT_CERTIFIED);
        }
        return profile;
    }

    private RunnerProfile findByUserId(Long userId) {
        return runnerProfileMapper.selectOne(
                new LambdaQueryWrapper<RunnerProfile>()
                        .eq(RunnerProfile::getUserId, userId));
    }
}
