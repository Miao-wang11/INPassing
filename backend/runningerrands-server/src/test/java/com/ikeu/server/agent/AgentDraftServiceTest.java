package com.ikeu.server.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikeu.common.exception.BusinessException;
import com.ikeu.common.exception.ForbiddenException;
import com.ikeu.model.entity.Task;
import com.ikeu.model.entity.User;
import com.ikeu.model.entity.UserAddress;
import com.ikeu.server.mapper.UserAddressMapper;
import com.ikeu.server.mapper.UserMapper;
import com.ikeu.server.service.CampusCatalogService;
import com.ikeu.server.service.ServiceOfferService;
import com.ikeu.server.service.TaskService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentDraftServiceTest {
    record Fixture(AgentDraftService draft, JdbcTemplate db, TaskService tasks,
                   ServiceOfferService offers, CampusCatalogService campuses, UserAddressMapper addresses) {}
    private Fixture create() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        CampusCatalogService campus = mock(CampusCatalogService.class);
        TaskService tasks = mock(TaskService.class);
        ServiceOfferService offers = mock(ServiceOfferService.class);
        UserMapper users = mock(UserMapper.class);
        UserAddressMapper addresses = mock(UserAddressMapper.class);
        when(campus.requireUserCampus(1L)).thenReturn(7L);
        when(users.selectById(1L)).thenReturn(User.builder().id(1L).balance(new BigDecimal("50.00")).build());
        when(addresses.selectById(10L)).thenReturn(UserAddress.builder().id(10L).userId(1L).build());
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        var draft = new AgentDraftService(db, campus, tasks, offers, users, addresses,
                new ObjectMapper(), validator);
        return new Fixture(draft, db, tasks, offers, campus, addresses);
    }
    private AgentDraftService.DraftInput publicTask(BigDecimal tip, Long addressId) {
        return new AgentDraftService.DraftInput("PUBLIC_TASK",
                new AgentDraftService.TaskInput("校内代办","资料打印", "打印资料", "图书馆",
                        addressId, null, tip, BigDecimal.ZERO, BigDecimal.ZERO, 60), null);
    }

    @Test
    void noBusinessWritesOnPreviewAndFeeValidation() {
        var f = create();
        assertThrows(BusinessException.class, () -> f.draft.prepare(1L,
                publicTask(new BigDecimal("51.00"),10L)));
        assertThrows(ForbiddenException.class, () -> f.draft.prepare(1L,
                publicTask(new BigDecimal("10.00"),11L)));
        assertThrows(BusinessException.class, () -> f.draft.prepare(1L,
                publicTask(BigDecimal.ZERO,10L)));
        when(f.db.update(anyString(), any(Object[].class))).thenReturn(1);
        var result = f.draft.prepare(1L, publicTask(new BigDecimal("10.00"),10L));
        assertEquals("PUBLIC_TASK", result.mode());
        assertEquals(new BigDecimal("10.00"), result.total());
        assertEquals(64, result.confirmToken().length());
        verifyNoInteractions(f.tasks);
        verifyNoInteractions(f.offers);
    }

    @Test
    void offerPreviewUsesActualJavaPriceAndPreventsSelfBooking() {
        var f = create();
        var start = LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).minusHours(1);
        var finish = start.plusDays(1);
        when(f.offers.detail(1L, 7L)).thenReturn(new ServiceOfferService.OfferView(
                7L, 2L, 7L, "学生","校内代办","资料打印","打印", "黑白",
                new BigDecimal("4.50"),start,finish,1));
        when(f.db.update(anyString(), any(Object[].class))).thenReturn(1);
        var input = new AgentDraftService.DraftInput("SERVICE_OFFER", null,
                new AgentDraftService.OfferInput(7L,"图书馆",10L,new BigDecimal("3.00"),null));
        var preview = f.draft.prepare(1L,input);
        assertEquals(new BigDecimal("7.50"),preview.total());
        verifyNoInteractions(f.tasks);
        when(f.offers.detail(1L,7L)).thenReturn(new ServiceOfferService.OfferView(
                7L,1L,7L,"本人","校内代办","资料打印","打印","本人",
                new BigDecimal("4.50"),start,finish,1));
        assertThrows(ForbiddenException.class, () -> f.draft.prepare(1L,input));
    }
}
