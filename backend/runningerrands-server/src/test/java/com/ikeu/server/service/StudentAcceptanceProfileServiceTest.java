package com.ikeu.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ikeu.common.constant.StatusConstant;
import com.ikeu.common.exception.ForbiddenException;
import com.ikeu.common.exception.NotFoundException;
import com.ikeu.model.entity.RunnerProfile;
import com.ikeu.model.entity.User;
import com.ikeu.server.mapper.RunnerProfileMapper;
import com.ikeu.server.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StudentAcceptanceProfileServiceTest {
    private UserMapper users;
    private RunnerProfileMapper profiles;
    private StudentAcceptanceProfileService service;

    @BeforeEach
    void setup() {
        users = mock(UserMapper.class);
        profiles = mock(RunnerProfileMapper.class);
        service = new StudentAcceptanceProfileService(users, profiles);
    }

    private User student(int certification, int status) {
        return User.builder().id(11L).isCertify(certification).status(status).build();
    }

    @Test
    void verifiedStudentGetsProfileOnFirstAccept() {
        when(users.selectById(11L)).thenReturn(student(2, 1));
        RunnerProfile created = RunnerProfile.builder().userId(11L)
                .verifyStatus(2).isOnline(1).creditScore(100).build();
        when(profiles.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, created);

        assertSame(created, service.ensureForAccept(11L));
        verify(profiles).createForCertifiedStudentIfAbsent(11L);
        verify(profiles, times(2)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void unverifiedStudentCannotCreateProfile() {
        when(users.selectById(11L)).thenReturn(student(StatusConstant.CERTIFY_AUDITING, 1));
        assertThrows(ForbiddenException.class, () -> service.ensureForAccept(11L));
        verifyNoInteractions(profiles);
    }

    @Test
    void disabledStudentCannotCreateProfile() {
        when(users.selectById(11L)).thenReturn(student(2, 0));
        assertThrows(ForbiddenException.class, () -> service.ensureForAccept(11L));
        verifyNoInteractions(profiles);
    }

    @Test
    void missingStudentCannotCreateProfile() {
        assertThrows(NotFoundException.class, () -> service.ensureForAccept(11L));
        verifyNoInteractions(profiles);
    }

    @Test
    void existingOfflineOrBannedProfileIsNeverReset() {
        when(users.selectById(11L)).thenReturn(student(2, 1));
        RunnerProfile existing = RunnerProfile.builder().userId(11L)
                .verifyStatus(2).isOnline(0).isBanned(1).creditScore(45).build();
        when(profiles.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        assertSame(existing, service.ensureForAccept(11L));
        assertEquals(0, existing.getIsOnline());
        assertEquals(1, existing.getIsBanned());
        assertEquals(45, existing.getCreditScore());
        verify(profiles, never()).createForCertifiedStudentIfAbsent(any());
    }

    @Test
    void concurrentExistingProfileWinsWithoutResettingIt() {
        when(users.selectById(11L)).thenReturn(student(2, 1));
        RunnerProfile concurrent = RunnerProfile.builder().userId(11L)
                .verifyStatus(2).isOnline(0).isBanned(1).build();
        when(profiles.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, concurrent);
        when(profiles.createForCertifiedStudentIfAbsent(11L)).thenReturn(0);
        assertSame(concurrent, service.ensureForAccept(11L));
    }

    @Test
    void revokedCertificationBetweenReadsIsRejected() {
        when(users.selectById(11L)).thenReturn(student(2, 1));
        when(profiles.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(profiles.createForCertifiedStudentIfAbsent(11L)).thenReturn(0);
        assertThrows(ForbiddenException.class, () -> service.ensureForAccept(11L));
    }
}
