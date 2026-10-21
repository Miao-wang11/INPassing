package com.ikeu.server.agent;

import com.ikeu.common.exception.ForbiddenException;
import com.ikeu.common.result.PageResult;
import com.ikeu.model.entity.Task;
import com.ikeu.model.entity.User;
import com.ikeu.model.vo.TaskListVO;
import com.ikeu.server.mapper.RunnerProfileMapper;
import com.ikeu.server.mapper.TaskMapper;
import com.ikeu.server.mapper.UserMapper;
import com.ikeu.server.service.CampusCatalogService;
import com.ikeu.server.service.ServiceOfferService;
import com.ikeu.server.service.TaskService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentReadOnlyServiceTest {

    @Test
    void searchRedactsContactAndDeniesCrossCampus() {
        var campuses = mock(CampusCatalogService.class);
        var tasks = mock(TaskService.class);
        var offers = mock(ServiceOfferService.class);
        var service = new AgentReadOnlyService(campuses, tasks, offers,
                mock(RunnerProfileMapper.class), mock(TaskMapper.class), mock(UserMapper.class));
        when(campuses.requireUserCampus(3L)).thenReturn(7L);
        var item = TaskListVO.builder().taskId(22L).campusId(7L).publisherId(4L).status(1)
                .type("校内代办").subType("资料打印").publicDesc("请联系13800138000")
                .pickupAddress("图书馆 301室").deliveryAddress("第三宿舍 3层301室")
                .expireTime(LocalDateTime.now().plusHours(1)).build();
        when(tasks.searchTasks(any(), any(), isNull(), isNull(), eq(1), eq(10)))
                .thenReturn(new PageResult<>(1L, List.of(item)));
        var data = service.searchTasks(3L, "打印", null, 1, 10);
        assertEquals(1, data.getRecords().size());
        assertFalse(data.getRecords().get(0).publicDesc().contains("13800138000"));
        assertFalse(data.getRecords().get(0).deliveryArea().contains("301"));
        item.setCampusId(8L);
        assertThrows(ForbiddenException.class, () -> service.searchTasks(3L, "打印", null, 1, 10));
    }

    @Test
    void eligibilityNeverInitializesMissingRunnerProfile() {
        var campuses = mock(CampusCatalogService.class);
        var mapper = mock(TaskMapper.class);
        var profiles = mock(RunnerProfileMapper.class);
        var users = mock(UserMapper.class);
        var service = new AgentReadOnlyService(campuses, mock(TaskService.class),
                mock(ServiceOfferService.class), profiles, mapper, users);
        when(campuses.requireUserCampus(3L)).thenReturn(7L);
        var task = Task.builder().id(22L).campusId(7L).publisherId(4L).status(1)
                .expireTime(LocalDateTime.now().plusHours(1)).build();
        when(mapper.selectById(22L)).thenReturn(task);
        when(users.selectById(3L)).thenReturn(User.builder().id(3L).status(1).isCertify(2).build());
        assertTrue(service.eligibility(3L, 22L).canAttempt());
        verify(profiles, never()).createForCertifiedStudentIfAbsent(anyLong());
        task.setCampusId(8L);
        assertThrows(ForbiddenException.class, () -> service.eligibility(3L, 22L));
    }
    @Test
    void publicDetailRejectsReservedTasksAndHidesPrivateFields() {
        var campuses = mock(CampusCatalogService.class);
        var mapper = mock(TaskMapper.class);
        var service = new AgentReadOnlyService(campuses, mock(TaskService.class),
                mock(ServiceOfferService.class), mock(RunnerProfileMapper.class), mapper, mock(UserMapper.class));
        when(campuses.requireUserCampus(3L)).thenReturn(7L);
        var task = Task.builder().id(88L).campusId(7L).publisherId(4L).status(1)
                .privateNote("do-not-leak").pickupCode("1234").contactPhone("13800138000")
                .publicDesc("公开打印任务").pickupAddress("宿舍楼 301室")
                .deliveryAddress("图书馆").expireTime(LocalDateTime.now().plusHours(1)).build();
        when(mapper.selectById(88L)).thenReturn(task);
        var detail = service.taskDetail(3L, 88L);
        assertEquals("公开打印任务", detail.publicDesc());
        assertFalse(detail.pickupArea().contains("301"));
        assertFalse(detail.toString().contains("do-not-leak"));
        assertFalse(detail.toString().contains("13800138000"));
        task.setReservedRunnerId(5L);
        assertThrows(ForbiddenException.class, () -> service.taskDetail(3L, 88L));
        task.setReservedRunnerId(null);
        task.setCampusId(9L);
        assertThrows(ForbiddenException.class, () -> service.taskDetail(3L, 88L));
    }
    @Test
    void ownStorefrontIsNotRecommendedOrReturnedAsMatch() {
        var campuses = mock(CampusCatalogService.class);
        var offers = mock(ServiceOfferService.class);
        when(campuses.requireUserCampus(3L)).thenReturn(7L);
        var own = new ServiceOfferService.OfferView(50L,3L,7L,"本人",
                "校内代办","资料打印","本人打印","打印",new java.math.BigDecimal("2.00"),
                LocalDateTime.now().minusHours(1),LocalDateTime.now().plusDays(1),1);
        var other = new ServiceOfferService.OfferView(51L,4L,7L,"其他学生",
                "校内代办","资料打印","打印","打印",new java.math.BigDecimal("3.00"),
                LocalDateTime.now().minusHours(1),LocalDateTime.now().plusDays(1),1);
        when(offers.hall(3L, "校内代办",1,10))
                .thenReturn(new PageResult<>(2L,List.of(own,other)));
        when(offers.detail(3L,50L)).thenReturn(own);
        var service = new AgentReadOnlyService(campuses,mock(TaskService.class),offers,
                mock(RunnerProfileMapper.class),mock(TaskMapper.class),mock(UserMapper.class));
        var actual = service.offers(3L,"校内代办",1,10);
        assertEquals(1,actual.getRecords().size());
        assertEquals(51L,actual.getRecords().get(0).offerId());
        assertThrows(ForbiddenException.class, () -> service.offerDetail(3L,50L));
    }
}
