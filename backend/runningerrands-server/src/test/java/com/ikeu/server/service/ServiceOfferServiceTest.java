package com.ikeu.server.service;

import com.ikeu.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceOfferServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final CampusCatalogService campus=mock(CampusCatalogService.class);
    private final TaskService tasks=mock(TaskService.class);
    private final TaskOrderService orders=mock(TaskOrderService.class);
    private final ServiceOfferService subject=new ServiceOfferService(jdbc,campus,tasks,orders);

    @BeforeEach void campus() {
        when(campus.requireUserCampus(10L)).thenReturn(101L);
    }
    private ServiceOfferService.OfferForm form(BigDecimal price) {
        return new ServiceOfferService.OfferForm("校内代办","图书馆还书",
                "可代还图书","只在校内帮忙",price,
                LocalDateTime.now().plusHours(1),LocalDateTime.now().plusHours(2));
    }

    @Test void negativePriceCannotPersist() {
        assertThrows(BusinessException.class,()->subject.publish(10L,form(new BigDecimal("-1.00"))));
        verifyNoInteractions(jdbc,tasks,orders);
    }
    @Test void fractionalCentCannotPersist() {
        assertThrows(BusinessException.class,()->subject.publish(10L,form(new BigDecimal("2.001"))));
        verifyNoInteractions(jdbc,tasks,orders);
    }
    @Test void badBookingKeyNeverTouchesDatabaseOrTask() {
        assertThrows(BusinessException.class,()->subject.book(10L,99L,
                new ServiceOfferService.BookingForm("x",42L,"图书馆",BigDecimal.ZERO,"","123456")));
        verifyNoInteractions(jdbc,tasks,orders);
    }
    @Test void invalidStatusFailsBeforeUpdate() {
        assertThrows(BusinessException.class,()->subject.changeStatus(10L,3L,10));
        verifyNoInteractions(jdbc);
    }
}
