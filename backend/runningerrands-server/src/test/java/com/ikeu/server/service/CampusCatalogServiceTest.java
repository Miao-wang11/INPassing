package com.ikeu.server.service;

import com.ikeu.common.exception.ForbiddenException;
import com.ikeu.model.entity.Task;
import com.ikeu.model.entity.User;
import com.ikeu.server.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Prevent unreviewed, cross-campus and legacy-unbound identity bypasses. */
class CampusCatalogServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final UserMapper users=mock(UserMapper.class);
    private final CampusCatalogService campuses=new CampusCatalogService(jdbc,users);

    @Test void certifiedMemberOfSameActiveCampusIsAllowed() {
        User user=new User();
        user.setId(12L); user.setStatus(1); user.setIsCertify(2); user.setCampusId(20L);
        when(users.selectById(12L)).thenReturn(user);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(20L))).thenReturn(1);
        Task task=new Task(); task.setCampusId(20L);
        assertDoesNotThrow(()->campuses.requireSameCampus(task,12L));
        task.setCampusId(21L);
        assertThrows(ForbiddenException.class,()->campuses.requireSameCampus(task,12L));
    }

    @Test void pendingCertificationCannotActAsApproved() {
        User user=new User();
        user.setStatus(1); user.setIsCertify(1); user.setPendingCampusId(20L);
        when(users.selectById(12L)).thenReturn(user);
        assertThrows(ForbiddenException.class,()->campuses.requireUserCampus(12L));
        verifyNoInteractions(jdbc);
    }

    @Test void verifiedLegacyUserWithoutApprovedCampusFailsClosed() {
        User user=new User();
        user.setStatus(1); user.setIsCertify(2);
        when(users.selectById(12L)).thenReturn(user);
        assertThrows(ForbiddenException.class,()->campuses.requireUserCampus(12L));
    }

    @Test void disabledCampusCannotBeUsed() {
        User user=new User();
        user.setStatus(1); user.setIsCertify(2); user.setCampusId(20L);
        when(users.selectById(12L)).thenReturn(user);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(20L))).thenReturn(0);
        assertThrows(RuntimeException.class,()->campuses.requireUserCampus(12L));
    }
}
