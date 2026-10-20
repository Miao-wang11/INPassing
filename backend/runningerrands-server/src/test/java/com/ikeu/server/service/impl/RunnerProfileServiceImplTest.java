package com.ikeu.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ikeu.common.constant.MessageConstant;
import com.ikeu.common.exception.BusinessException;
import com.ikeu.common.exception.ForbiddenException;
import com.ikeu.common.exception.NotFoundException;
import com.ikeu.model.dto.OnTimeStatsDTO;
import com.ikeu.model.dto.SetMaxOrdersDTO;
import com.ikeu.model.entity.RunnerProfile;
import com.ikeu.model.entity.User;
import com.ikeu.model.vo.RunnerInfoVO;
import com.ikeu.model.vo.RunnerPerformanceVO;
import com.ikeu.model.vo.RunnerRankingVO;
import com.ikeu.server.mapper.RunnerProfileMapper;
import com.ikeu.server.mapper.TaskOrderMapper;
import com.ikeu.server.mapper.TransactionRecordMapper;
import com.ikeu.server.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RunnerProfileServiceImpl 单元测试。
 * 覆盖 getRunnerPerformance、getProfile、applyForRunner、goOnline、goOffline、setMaxOrders 的核心逻辑。
 */
@ExtendWith(MockitoExtension.class)
class RunnerProfileServiceImplTest {

    @Mock
    private RunnerProfileMapper runnerProfileMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private TransactionRecordMapper transactionRecordMapper;

    @Mock
    private TaskOrderMapper taskOrderMapper;

    private RunnerProfileServiceImpl runnerProfileService;

    @BeforeEach
    void setUp() throws Exception {
        runnerProfileService = new RunnerProfileServiceImpl(
                runnerProfileMapper, userMapper, transactionRecordMapper, taskOrderMapper);
        // Set the baseMapper on the parent ServiceImpl so that lambdaQuery() works
        Field baseMapperField = ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapperField.setAccessible(true);
        baseMapperField.set(runnerProfileService, runnerProfileMapper);
    }

    // ==================== getRunnerPerformance ====================

    @Test
    void getRunnerPerformance_profileNotFound_shouldReturnNull() {
        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        RunnerPerformanceVO result = runnerProfileService.getRunnerPerformance(999L);

        assertNull(result);
        verify(runnerProfileMapper).selectOne(any(LambdaQueryWrapper.class));
        verifyNoInteractions(userMapper, transactionRecordMapper, taskOrderMapper);
    }

    @Test
    void getRunnerPerformance_normalCase_shouldCalculateCorrectly() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).totalOrders(20).successOrders(18)
                .currentOrders(2).avgRating(BigDecimal.valueOf(4.5))
                .creditScore(85).build();
        User user = User.builder()
                .id(100L).nickname("测试跑腿员").avatarUrl("http://avatar.url").sex("男")
                .build();
        OnTimeStatsDTO onTimeStats = new OnTimeStatsDTO(10L, 8L); // 8/10 on-time = 80%

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);
        when(userMapper.selectById(100L)).thenReturn(user);
        when(transactionRecordMapper.sumIncomeByUserId(100L)).thenReturn(BigDecimal.valueOf(500));
        when(taskOrderMapper.countCompletedOnTime(100L)).thenReturn(onTimeStats);

        RunnerPerformanceVO result = runnerProfileService.getRunnerPerformance(100L);

        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        assertEquals(20, result.getTotalOrders());
        assertEquals(18, result.getSuccessOrders());
        // completionRate = 18/20 * 100 = 90.0
        assertEquals(90.0, result.getCompletionRate(), 0.001);
        // onTimeRate = 8/10 * 100 = 80.0
        assertEquals(80.0, result.getOnTimeRate(), 0.001);
        assertEquals(2, result.getCurrentOrders());
        assertEquals(4.5, result.getAvgRating(), 0.001);
        assertEquals(85, result.getCreditScore());
        assertEquals(BigDecimal.valueOf(500), result.getTotalEarnings());
        assertEquals("测试跑腿员", result.getNickname());
        assertEquals("http://avatar.url", result.getAvatarUrl());
        assertEquals("男", result.getSex());
    }

    @Test
    void getRunnerPerformance_allOrdersOnTime_shouldReturn100Percent() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).totalOrders(5).successOrders(5)
                .currentOrders(0).avgRating(BigDecimal.valueOf(5.0))
                .creditScore(100).build();
        OnTimeStatsDTO onTimeStats = new OnTimeStatsDTO(5L, 5L); // 5/5 on-time = 100%

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);
        when(userMapper.selectById(100L)).thenReturn(User.builder().id(100L).build());
        when(transactionRecordMapper.sumIncomeByUserId(100L)).thenReturn(BigDecimal.ZERO);
        when(taskOrderMapper.countCompletedOnTime(100L)).thenReturn(onTimeStats);

        RunnerPerformanceVO result = runnerProfileService.getRunnerPerformance(100L);

        assertEquals(100.0, result.getOnTimeRate(), 0.001);
        assertEquals(100.0, result.getCompletionRate(), 0.001);
    }

    @Test
    void getRunnerPerformance_zeroCompletedOrders_shouldReturnOnTimeRateZero() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).totalOrders(10).successOrders(5)
                .currentOrders(0).avgRating(BigDecimal.valueOf(4.0))
                .creditScore(80).build();
        OnTimeStatsDTO onTimeStats = new OnTimeStatsDTO(0L, 0L); // 0 completed orders

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);
        when(userMapper.selectById(100L)).thenReturn(User.builder().id(100L).build());
        when(transactionRecordMapper.sumIncomeByUserId(100L)).thenReturn(BigDecimal.ZERO);
        when(taskOrderMapper.countCompletedOnTime(100L)).thenReturn(onTimeStats);

        RunnerPerformanceVO result = runnerProfileService.getRunnerPerformance(100L);

        assertEquals(0.0, result.getOnTimeRate(), 0.001);
        assertEquals(50.0, result.getCompletionRate(), 0.001);
    }

    @Test
    void getRunnerPerformance_nullTotalInOnTimeStats_shouldDefaultToZero() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).totalOrders(5).successOrders(5)
                .currentOrders(0).avgRating(BigDecimal.valueOf(5.0))
                .creditScore(100).build();
        // total = null, onTime = null — should default to 0 and return 0% onTimeRate
        OnTimeStatsDTO onTimeStats = new OnTimeStatsDTO(null, null);

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);
        when(userMapper.selectById(100L)).thenReturn(User.builder().id(100L).build());
        when(transactionRecordMapper.sumIncomeByUserId(100L)).thenReturn(BigDecimal.ZERO);
        when(taskOrderMapper.countCompletedOnTime(100L)).thenReturn(onTimeStats);

        RunnerPerformanceVO result = runnerProfileService.getRunnerPerformance(100L);

        assertEquals(0.0, result.getOnTimeRate(), 0.001);
    }

    @Test
    void getRunnerPerformance_nullOnTimeInOnTimeStats_shouldDefaultToZero() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).totalOrders(5).successOrders(5)
                .currentOrders(0).avgRating(BigDecimal.valueOf(5.0))
                .creditScore(100).build();
        // total has value but onTime is null
        OnTimeStatsDTO onTimeStats = new OnTimeStatsDTO(10L, null);

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);
        when(userMapper.selectById(100L)).thenReturn(User.builder().id(100L).build());
        when(transactionRecordMapper.sumIncomeByUserId(100L)).thenReturn(BigDecimal.ZERO);
        when(taskOrderMapper.countCompletedOnTime(100L)).thenReturn(onTimeStats);

        RunnerPerformanceVO result = runnerProfileService.getRunnerPerformance(100L);

        // onTime defaults to 0, total = 10 > 0 => onTimeRate = 0/10 * 100 = 0.0
        assertEquals(0.0, result.getOnTimeRate(), 0.001);
    }

    @Test
    void getRunnerPerformance_zeroTotalOrdersInProfile_shouldReturnCompletionRateZero() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).totalOrders(0).successOrders(0)
                .currentOrders(0).avgRating(BigDecimal.valueOf(5.0))
                .creditScore(100).build();
        OnTimeStatsDTO onTimeStats = new OnTimeStatsDTO(0L, 0L);

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);
        when(userMapper.selectById(100L)).thenReturn(User.builder().id(100L).build());
        when(transactionRecordMapper.sumIncomeByUserId(100L)).thenReturn(BigDecimal.ZERO);
        when(taskOrderMapper.countCompletedOnTime(100L)).thenReturn(onTimeStats);

        RunnerPerformanceVO result = runnerProfileService.getRunnerPerformance(100L);

        assertEquals(0.0, result.getCompletionRate(), 0.001);
        assertEquals(0.0, result.getOnTimeRate(), 0.001);
    }

    @Test
    void getRunnerPerformance_avgRatingIsNull_shouldDefaultTo5() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).totalOrders(5).successOrders(5)
                .currentOrders(0).avgRating(null)
                .creditScore(100).build();
        OnTimeStatsDTO onTimeStats = new OnTimeStatsDTO(5L, 5L);

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);
        when(userMapper.selectById(100L)).thenReturn(User.builder().id(100L).build());
        when(transactionRecordMapper.sumIncomeByUserId(100L)).thenReturn(BigDecimal.ZERO);
        when(taskOrderMapper.countCompletedOnTime(100L)).thenReturn(onTimeStats);

        RunnerPerformanceVO result = runnerProfileService.getRunnerPerformance(100L);

        assertEquals(5.0, result.getAvgRating(), 0.001);
    }

    @Test
    void getRunnerPerformance_userNotFound_shouldUseEmptyStrings() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).totalOrders(5).successOrders(5)
                .currentOrders(0).avgRating(BigDecimal.valueOf(4.0))
                .creditScore(80).build();
        OnTimeStatsDTO onTimeStats = new OnTimeStatsDTO(5L, 4L);

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);
        when(userMapper.selectById(100L)).thenReturn(null); // user not found
        when(transactionRecordMapper.sumIncomeByUserId(100L)).thenReturn(BigDecimal.ZERO);
        when(taskOrderMapper.countCompletedOnTime(100L)).thenReturn(onTimeStats);

        RunnerPerformanceVO result = runnerProfileService.getRunnerPerformance(100L);

        assertNotNull(result);
        assertEquals("", result.getNickname());
        assertEquals("", result.getAvatarUrl());
        assertEquals("", result.getSex());
    }

    @Test
    void getRunnerPerformance_sumIncomeReturnsNull_shouldSetEarningsToNull() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).totalOrders(5).successOrders(5)
                .currentOrders(0).avgRating(BigDecimal.valueOf(4.0))
                .creditScore(80).build();
        OnTimeStatsDTO onTimeStats = new OnTimeStatsDTO(5L, 4L);

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);
        when(userMapper.selectById(100L)).thenReturn(User.builder().id(100L).build());
        when(transactionRecordMapper.sumIncomeByUserId(100L)).thenReturn(null); // null income
        when(taskOrderMapper.countCompletedOnTime(100L)).thenReturn(onTimeStats);

        RunnerPerformanceVO result = runnerProfileService.getRunnerPerformance(100L);

        assertNull(result.getTotalEarnings());
    }

    @Test
    void getRunnerPerformance_preciseOnTimeRateRounding_shouldRoundToTenths() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).totalOrders(5).successOrders(3)
                .currentOrders(0).avgRating(BigDecimal.valueOf(4.0))
                .creditScore(80).build();
        // 1 on-time out of 3 completed = 33.333...%, should round to 33.3
        OnTimeStatsDTO onTimeStats = new OnTimeStatsDTO(3L, 1L);

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);
        when(userMapper.selectById(100L)).thenReturn(User.builder().id(100L).build());
        when(transactionRecordMapper.sumIncomeByUserId(100L)).thenReturn(BigDecimal.ZERO);
        when(taskOrderMapper.countCompletedOnTime(100L)).thenReturn(onTimeStats);

        RunnerPerformanceVO result = runnerProfileService.getRunnerPerformance(100L);

        assertEquals(33.3, result.getOnTimeRate(), 0.001);
        // completionRate = 3/5 * 100 = 60.0
        assertEquals(60.0, result.getCompletionRate(), 0.001);
    }

    // ==================== getProfile ====================

    @Test
    void getProfile_normal_shouldReturnRunnerInfoVO() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).realName("张三").creditScore(80)
                .totalOrders(10).successOrders(8)
                .avgRating(BigDecimal.valueOf(4.5))
                .isOnline(1).maxConcurrentOrders(3).currentOrders(2)
                .build();

        // getByUserId will use baseMapper -> runnerProfileMapper via lambdaQuery()
        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);

        RunnerInfoVO result = runnerProfileService.getProfile(100L);

        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        assertEquals("张三", result.getRealName());
        assertEquals(80, result.getCreditScore());
        assertEquals(10, result.getTotalOrders());
        assertEquals(8, result.getSuccessOrders());
        assertEquals(4.5, result.getAvgRating(), 0.001);
    }

    @Test
    void getProfile_profileNotFound_shouldThrowForbiddenException() {
        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(ForbiddenException.class, () -> runnerProfileService.getProfile(999L));
    }

    @Test
    void getProfile_avgRatingNull_shouldNotFail() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).realName("李四")
                .avgRating(null).build();

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);

        RunnerInfoVO result = runnerProfileService.getProfile(100L);

        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        assertNull(result.getAvgRating());
    }

    // ==================== applyForRunner ====================

    @Test
    void applyForRunner_userNotFound_shouldThrowNotFoundException() {
        when(userMapper.selectById(100L)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> runnerProfileService.applyForRunner(100L));
    }

    @Test
    void applyForRunner_userNotCertified_shouldThrowForbiddenException() {
        User user = User.builder().id(100L).isCertify(0).build();
        when(userMapper.selectById(100L)).thenReturn(user);

        assertThrows(ForbiddenException.class, () -> runnerProfileService.applyForRunner(100L));
    }

    @Test
    void applyForRunner_existingProfileAuditing_shouldThrowBusinessException() {
        User user = User.builder().id(100L).isCertify(2).realName("张三").build();
        RunnerProfile existing = RunnerProfile.builder()
                .userId(100L).verifyStatus(1) // CERTIFY_AUDITING
                .build();

        when(userMapper.selectById(100L)).thenReturn(user);
        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        assertThrows(BusinessException.class, () -> runnerProfileService.applyForRunner(100L));
    }

    @Test
    void applyForRunner_existingProfileApproved_shouldThrowBusinessException() {
        User user = User.builder().id(100L).isCertify(2).realName("张三").build();
        RunnerProfile existing = RunnerProfile.builder()
                .userId(100L).verifyStatus(2) // CERTIFY_APPROVED
                .build();

        when(userMapper.selectById(100L)).thenReturn(user);
        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        assertThrows(BusinessException.class, () -> runnerProfileService.applyForRunner(100L));
    }

    @Test
    void applyForRunner_rejectedProfile_shouldReapply() {
        User user = User.builder().id(100L).isCertify(2).realName("张三").build();
        RunnerProfile rejected = RunnerProfile.builder()
                .id(1L).userId(100L).verifyStatus(3) // CERTIFY_REJECTED
                .build();

        when(userMapper.selectById(100L)).thenReturn(user);
        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(rejected);

        runnerProfileService.applyForRunner(100L);

        verify(runnerProfileMapper).updateById(argThat(p ->
                p.getVerifyStatus() == 1 && p.getUpdatedAt() != null));
    }

    @Test
    void applyForRunner_newProfile_shouldInsert() {
        User user = User.builder().id(100L).isCertify(2).realName("张三").build();

        when(userMapper.selectById(100L)).thenReturn(user);
        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        runnerProfileService.applyForRunner(100L);

        verify(runnerProfileMapper).insert(argThat(p ->
                p.getUserId() == 100L
                        && p.getVerifyStatus() == 1
                        && p.getCreditScore() == 100
                        && p.getTotalOrders() == 0
                        && p.getSuccessOrders() == 0
                        && p.getAvgRating().compareTo(BigDecimal.valueOf(5.0)) == 0
                        && p.getIsOnline() == 0
                        && p.getMaxConcurrentOrders() == 3
                        && p.getCurrentOrders() == 0
        ));
    }

    // ==================== goOnline ====================

    @Test
    void goOnline_profileNotFound_shouldThrowForbiddenException() {
        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(ForbiddenException.class, () -> runnerProfileService.goOnline(100L));
    }

    @Test
    void goOnline_notCertified_shouldThrowForbiddenException() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).verifyStatus(1).isOnline(0).build();

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);

        assertThrows(ForbiddenException.class, () -> runnerProfileService.goOnline(100L));
    }

    @Test
    void goOnline_alreadyOnline_shouldThrowBusinessException() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).verifyStatus(2).isOnline(1).build();

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);

        assertThrows(BusinessException.class, () -> runnerProfileService.goOnline(100L));
    }

    @Test
    void goOnline_success_shouldSetOnline() {
        RunnerProfile profile = RunnerProfile.builder()
                .id(1L).userId(100L).verifyStatus(2).isOnline(0).build();

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);

        runnerProfileService.goOnline(100L);

        verify(runnerProfileMapper).updateById(argThat(p ->
                p.getIsOnline() == 1));
    }

    // ==================== goOffline ====================

    @Test
    void goOffline_alreadyOffline_shouldThrowBusinessException() {
        RunnerProfile profile = RunnerProfile.builder()
                .userId(100L).isOnline(0).build();

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);

        assertThrows(BusinessException.class, () -> runnerProfileService.goOffline(100L));
    }

    @Test
    void goOffline_success_shouldSetOffline() {
        RunnerProfile profile = RunnerProfile.builder()
                .id(1L).userId(100L).isOnline(1).build();

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);

        runnerProfileService.goOffline(100L);

        verify(runnerProfileMapper).updateById(argThat(p ->
                p.getIsOnline() == 0));
    }

    // ==================== setMaxOrders ====================

    @Test
    void setMaxOrders_success_shouldUpdateMaxOrders() {
        RunnerProfile profile = RunnerProfile.builder()
                .id(1L).userId(100L).maxConcurrentOrders(3).build();
        SetMaxOrdersDTO dto = new SetMaxOrdersDTO();
        dto.setMaxOrders(5);

        when(runnerProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(profile);

        runnerProfileService.setMaxOrders(100L, dto);

        verify(runnerProfileMapper).updateById(argThat(p ->
                p.getMaxConcurrentOrders() == 5));
    }

    // ==================== getLeaderboard ====================

    @Test
    void getLeaderboard_noProfiles_shouldReturnEmptyList() {
        when(runnerProfileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<RunnerRankingVO> result = runnerProfileService.getLeaderboard("rating", 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getLeaderboard_hasProfiles_shouldReturnSortedResults() {
        RunnerProfile p1 = RunnerProfile.builder()
                .userId(1L).totalOrders(10).successOrders(9)
                .avgRating(BigDecimal.valueOf(4.8)).currentOrders(0)
                .creditScore(100).build();
        RunnerProfile p2 = RunnerProfile.builder()
                .userId(2L).totalOrders(5).successOrders(5)
                .avgRating(BigDecimal.valueOf(4.5)).currentOrders(0)
                .creditScore(95).build();

        User u1 = User.builder().id(1L).nickname("跑腿员A").avatarUrl("url1").build();
        User u2 = User.builder().id(2L).nickname("跑腿员B").avatarUrl("url2").build();

        when(runnerProfileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(p1, p2));
        when(userMapper.selectBatchIds(Arrays.asList(1L, 2L)))
                .thenReturn(Arrays.asList(u1, u2));
        when(transactionRecordMapper.sumIncomeByUserIds(anyList()))
                .thenReturn(Collections.emptyList());

        List<RunnerRankingVO> result = runnerProfileService.getLeaderboard("rating", 10);

        assertNotNull(result);
        assertEquals(2, result.size());
        // Should be sorted by avgRating descending: p1 (4.8) first, p2 (4.5) second
        assertEquals(1L, result.get(0).getUserId());
        assertEquals(2L, result.get(1).getUserId());
    }

    @Test
    void getLeaderboard_limitTrim_shouldRespectLimit() {
        RunnerProfile p1 = RunnerProfile.builder()
                .userId(1L).totalOrders(10).successOrders(9)
                .avgRating(BigDecimal.valueOf(4.0)).currentOrders(0)
                .creditScore(100).build();
        RunnerProfile p2 = RunnerProfile.builder()
                .userId(2L).totalOrders(5).successOrders(5)
                .avgRating(BigDecimal.valueOf(4.5)).currentOrders(0)
                .creditScore(95).build();

        User u1 = User.builder().id(1L).build();
        User u2 = User.builder().id(2L).build();

        when(runnerProfileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(p1, p2));
        when(userMapper.selectBatchIds(Arrays.asList(1L, 2L)))
                .thenReturn(Arrays.asList(u1, u2));
        when(transactionRecordMapper.sumIncomeByUserIds(anyList()))
                .thenReturn(Collections.emptyList());

        // Limit 1 should return only 1 result
        List<RunnerRankingVO> result = runnerProfileService.getLeaderboard("completion", 1);

        assertEquals(1, result.size());
    }

    // ==================== decrementCurrentOrders ====================

    @Test
    void decrementCurrentOrders_shouldDelegateToMapper() {
        runnerProfileService.decrementCurrentOrders(100L);

        verify(runnerProfileMapper).decrementCurrentOrders(100L);
    }
}
