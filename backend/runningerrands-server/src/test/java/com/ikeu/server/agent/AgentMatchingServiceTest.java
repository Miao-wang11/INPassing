package com.ikeu.server.agent;

import com.ikeu.common.constant.StatusConstant;
import com.ikeu.common.exception.BusinessException;
import com.ikeu.common.result.PageResult;
import com.ikeu.model.entity.Task;
import com.ikeu.model.entity.User;
import com.ikeu.model.vo.TaskListVO;
import com.ikeu.server.mapper.RunnerProfileMapper;
import com.ikeu.server.mapper.TaskMapper;
import com.ikeu.server.mapper.UserMapper;
import com.ikeu.server.service.CampusCatalogService;
import com.ikeu.server.service.ServiceOfferService;
import com.ikeu.server.service.TaskOrderService;
import com.ikeu.server.service.TaskService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentMatchingServiceTest {
    @Test
    void candidatePrecheckExcludesSelfExpiredAndGenderMismatchWithoutBusinessMutation() {
        var campuses=mock(CampusCatalogService.class);
        var tasks=mock(TaskService.class);
        var mapper=mock(TaskMapper.class);
        var users=mock(UserMapper.class);
        var profile=mock(RunnerProfileMapper.class);
        when(campuses.requireUserCampus(9L)).thenReturn(7L);
        when(users.selectById(9L)).thenReturn(User.builder()
                .id(9L).status(StatusConstant.ENABLE)
                .isCertify(StatusConstant.CERTIFY_APPROVED).sex("男").build());
        var future=LocalDateTime.now().plusHours(3);
        var cards=List.of(
                TaskListVO.builder().taskId(41L).publisherId(9L).campusId(7L)
                        .status(StatusConstant.TASK_WAITING).expireTime(future).build(),
                TaskListVO.builder().taskId(42L).publisherId(2L).campusId(7L)
                        .status(StatusConstant.TASK_WAITING).expireTime(LocalDateTime.now().minusMinutes(1)).build(),
                TaskListVO.builder().taskId(43L).publisherId(2L).campusId(7L)
                        .status(StatusConstant.TASK_WAITING).expireTime(future).build(),
                TaskListVO.builder().taskId(44L).publisherId(3L).campusId(7L)
                        .status(StatusConstant.TASK_WAITING).expireTime(future)
                        .publicDesc("联系13800138000").pickupAddress("图书馆 301室")
                        .deliveryAddress("教学楼").build());
        when(tasks.searchTasks(isNull(),isNull(),isNull(),isNull(),eq(1),eq(20)))
                .thenReturn(new PageResult<>(4L,cards));
        when(mapper.selectById(43L)).thenReturn(Task.builder()
                .id(43L).campusId(7L).publisherId(2L).status(StatusConstant.TASK_WAITING)
                .expireTime(future).requireSex("女").build());
        when(mapper.selectById(44L)).thenReturn(Task.builder()
                .id(44L).campusId(7L).publisherId(3L).status(StatusConstant.TASK_WAITING)
                .expireTime(future).build());
        var adapter=new AgentReadOnlyService(campuses,tasks,mock(ServiceOfferService.class),
                profile,mapper,users);
        var batch=adapter.matchingCandidates(9L,1,20);
        assertEquals(4,batch.examined());
        assertEquals(1,batch.records().size());
        assertEquals(44L,batch.records().get(0).taskId());
        assertFalse(batch.records().get(0).publicDesc().contains("13800138000"));
        assertFalse(batch.records().get(0).pickupArea().contains("301"));
        verify(tasks,never()).publishTask(anyLong(),any());
        verify(profile,never()).insert(any());
    }

    @Test
    void candidateBatchFailsClosedForCrossCampus() {
        var campuses=mock(CampusCatalogService.class);
        var tasks=mock(TaskService.class);
        when(campuses.requireUserCampus(9L)).thenReturn(7L);
        when(tasks.searchTasks(isNull(),isNull(),isNull(),isNull(),eq(1),eq(20)))
                .thenReturn(new PageResult<>(1L,List.of(TaskListVO.builder().taskId(78L)
                        .campusId(8L).publisherId(1L).status(1)
                        .expireTime(LocalDateTime.now().plusDays(1)).build())));
        var adapter=new AgentReadOnlyService(campuses,tasks,mock(ServiceOfferService.class),
                mock(RunnerProfileMapper.class),mock(TaskMapper.class),mock(UserMapper.class));
        assertThrows(com.ikeu.common.exception.ForbiddenException.class,
                () -> adapter.matchingCandidates(9L,1,20));
    }

    @Test
    void explicitStudentAcceptReusesExistingServiceAndDoesNotClaimInRanking() {
        var orders=mock(TaskOrderService.class);
        var controller=new AgentMatchController(orders);
        com.ikeu.common.context.BaseContext.setCurrentId(9L);
        try {
            when(orders.acceptOrder(9L,44L)).thenReturn(123L);
            var accepted=controller.accept(44L);
            assertEquals(123L,accepted.getData().getOrderId());
            // Competing runner won the task: original Service exception is not swallowed.
            when(orders.acceptOrder(9L,45L))
                    .thenThrow(new BusinessException("任务已被别人接走"));
            assertThrows(BusinessException.class,() -> controller.accept(45L));
            verify(orders,times(2)).acceptOrder(eq(9L),anyLong());
        } finally {
            com.ikeu.common.context.BaseContext.removeCurrentId();
        }
    }
}
