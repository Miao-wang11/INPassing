package com.ikeu.server.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ikeu.common.constant.CreditConstant;
import com.ikeu.common.constant.RedisConstant;
import com.ikeu.model.entity.CreditLog;
import com.ikeu.model.entity.RunnerProfile;
import com.ikeu.server.mapper.CreditLogMapper;
import com.ikeu.server.mapper.RunnerProfileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CreditRecoveryTask 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class CreditRecoveryTaskTest {

    @Mock
    private RunnerProfileMapper runnerProfileMapper;

    @Mock
    private CreditLogMapper creditLogMapper;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private CreditRecoveryTask task;

    @BeforeEach
    void setUp() {
        task = new CreditRecoveryTask(runnerProfileMapper, creditLogMapper, redissonClient);
    }

    @Test
    void recoverExpiredFreezes_lockAcquired_shouldRecoverFrozenRunners() throws Exception {
        when(redissonClient.getLock(RedisConstant.CREDIT_RECOVERY_LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        RunnerProfile runner = RunnerProfile.builder()
                .userId(200L).id(10L).creditScore(50).isBanned(1)
                .banUntil(LocalDateTime.now().minusDays(1))
                .build();
        RunnerProfile restored = RunnerProfile.builder()
                .userId(200L).id(10L).creditScore(CreditConstant.CREDIT_FREEZE_THRESHOLD).isBanned(0)
                .build();

        when(runnerProfileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(runner));
        when(runnerProfileMapper.restoreCreditAndUnfreeze(eq(200L), eq(CreditConstant.CREDIT_FREEZE_THRESHOLD)))
                .thenReturn(1);
        when(runnerProfileMapper.selectById(10L)).thenReturn(restored);

        task.recoverExpiredFreezes();

        verify(runnerProfileMapper).restoreCreditAndUnfreeze(200L, CreditConstant.CREDIT_FREEZE_THRESHOLD);

        ArgumentCaptor<CreditLog> logCaptor = ArgumentCaptor.forClass(CreditLog.class);
        verify(creditLogMapper).insert(logCaptor.capture());
        CreditLog creditLog = logCaptor.getValue();

        assertEquals(200L, creditLog.getRunnerId());
        assertEquals(10, creditLog.getDelta());
        assertEquals(50, creditLog.getScoreBefore());
        assertEquals(CreditConstant.CREDIT_FREEZE_THRESHOLD, creditLog.getScoreAfter());
        assertEquals(CreditConstant.ReasonType.RECOVER, creditLog.getReasonType());

        verify(lock).unlock();
    }

    @Test
    void recoverExpiredFreezes_restoreReturnsZero_shouldSkipLog() throws Exception {
        when(redissonClient.getLock(RedisConstant.CREDIT_RECOVERY_LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        RunnerProfile runner = RunnerProfile.builder()
                .userId(200L).id(10L).creditScore(50).isBanned(1)
                .banUntil(LocalDateTime.now().minusDays(1))
                .build();
        when(runnerProfileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(runner));
        when(runnerProfileMapper.restoreCreditAndUnfreeze(eq(200L), anyInt())).thenReturn(0);

        task.recoverExpiredFreezes();

        verify(creditLogMapper, never()).insert(any());
        verify(runnerProfileMapper, never()).selectById(anyLong());
        verify(lock).unlock();
    }

    @Test
    void recoverExpiredFreezes_lockNotAcquired_shouldReturnWithoutAction() throws Exception {
        when(redissonClient.getLock(RedisConstant.CREDIT_RECOVERY_LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(false);

        task.recoverExpiredFreezes();

        verify(runnerProfileMapper, never()).selectList(any());
        verify(creditLogMapper, never()).insert(any());
    }

    @Test
    void recoverExpiredFreezes_lockInterrupted_shouldHandleInterruption() throws Exception {
        when(redissonClient.getLock(RedisConstant.CREDIT_RECOVERY_LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenThrow(new InterruptedException("interrupted"));

        task.recoverExpiredFreezes();

        assertTrue(Thread.interrupted());
        verify(runnerProfileMapper, never()).selectList(any());
        verify(creditLogMapper, never()).insert(any());
    }

    @Test
    void recoverExpiredFreezes_noFrozenRunners_shouldDoNothing() throws Exception {
        when(redissonClient.getLock(RedisConstant.CREDIT_RECOVERY_LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(runnerProfileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        task.recoverExpiredFreezes();

        verify(runnerProfileMapper).selectList(any());
        verify(creditLogMapper, never()).insert(any());
        verify(lock).unlock();
    }

    @Test
    void recoverExpiredFreezes_runnerWithNullCreditScore_shouldUseInitialScore() throws Exception {
        when(redissonClient.getLock(RedisConstant.CREDIT_RECOVERY_LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(0, 60, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        RunnerProfile runner = RunnerProfile.builder()
                .userId(300L).id(20L).creditScore(null).isBanned(1)
                .banUntil(LocalDateTime.now().minusDays(1))
                .build();
        RunnerProfile restored = RunnerProfile.builder()
                .userId(300L).id(20L).creditScore(CreditConstant.CREDIT_FREEZE_THRESHOLD).isBanned(0)
                .build();

        when(runnerProfileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(runner));
        when(runnerProfileMapper.restoreCreditAndUnfreeze(eq(300L), anyInt())).thenReturn(1);
        when(runnerProfileMapper.selectById(20L)).thenReturn(restored);

        task.recoverExpiredFreezes();

        ArgumentCaptor<CreditLog> logCaptor = ArgumentCaptor.forClass(CreditLog.class);
        verify(creditLogMapper).insert(logCaptor.capture());
        CreditLog creditLog = logCaptor.getValue();

        int expectedDelta = CreditConstant.CREDIT_FREEZE_THRESHOLD - CreditConstant.CREDIT_INITIAL;
        assertEquals(expectedDelta, creditLog.getDelta());
        assertEquals(CreditConstant.CREDIT_INITIAL, creditLog.getScoreBefore());
        assertEquals(CreditConstant.CREDIT_FREEZE_THRESHOLD, creditLog.getScoreAfter());
    }
}
