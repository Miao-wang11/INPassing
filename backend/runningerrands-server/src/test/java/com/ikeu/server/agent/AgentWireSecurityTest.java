package com.ikeu.server.agent;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.ikeu.server.service.CampusCatalogService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** No MySQL/Redis network needed: verifies transport wire and identity isolation logic. */
class AgentWireSecurityTest {
    private static final String SECRET = "unit-test-agent-secret-0123456789-abcdefghijk";

    @Test
    void signedPrincipalAndRequestRejectForgery() {
        var wire = new AgentWireSecurity(SECRET);
        String principal = wire.issuePrincipal(12L, 8L);
        assertEquals(12L, wire.verifyPrincipal(principal).userId());
        assertEquals(8L, wire.verifyPrincipal(principal).campusId());
        String path = "/api/internal/agent/v1/tasks/search?keyword=test";
        String time = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String mac = wire.signature("GET", path, principal, time, nonce);
        assertEquals(12L, wire.verifyRequest("GET", path, principal, time, nonce, mac).userId());
        assertThrows(SecurityException.class, () -> wire.verifyRequest(
                "GET", path + "&page=9", principal, time, nonce, mac));
        assertThrows(SecurityException.class, () -> wire.verifyRequest(
                "GET", path, principal, "1", nonce, mac));
        assertThrows(SecurityException.class, () -> wire.verifyRequest(
                "POST", path, principal, time, nonce, mac));
        assertThrows(SecurityException.class, () -> wire.verifyPrincipal(principal.replace('a', 'z')));
        assertFalse(new AgentWireSecurity("").configured());
    }

    @Test
    void interceptorRejectsReplayAndCampusChange() {
        var wire = new AgentWireSecurity(SECRET);
        var campuses = mock(CampusCatalogService.class);
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(true).thenReturn(false);
        when(campuses.requireUserCampus(12L)).thenReturn(8L).thenReturn(9L);
        var interceptor = new AgentInternalAuthInterceptor(wire, campuses, redis);
        String token = wire.issuePrincipal(12L, 8L);
        String path = "/api/internal/agent/v1/tasks/search";
        String time = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        var request = new MockHttpServletRequest("GET", path);
        request.addHeader("X-Agent-Principal", token);
        request.addHeader("X-Agent-Timestamp", time);
        request.addHeader("X-Agent-Nonce", nonce);
        request.addHeader("X-Agent-Signature", wire.signature("GET", path, token, time, nonce));
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(12L, com.ikeu.common.context.BaseContext.getCurrentId());
        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
        assertNull(com.ikeu.common.context.BaseContext.getCurrentId());
        var replay = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, replay, new Object()));
        assertEquals(401, replay.getStatus());
        // A new nonce cannot reuse an outdated campus membership.
        when(ops.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        var changed = new MockHttpServletRequest("GET", path);
        String freshNonce = UUID.randomUUID().toString();
        changed.addHeader("X-Agent-Principal", token);
        changed.addHeader("X-Agent-Timestamp", time);
        changed.addHeader("X-Agent-Nonce", freshNonce);
        changed.addHeader("X-Agent-Signature", wire.signature("GET", path, token, time, freshNonce));
        assertFalse(interceptor.preHandle(changed, new MockHttpServletResponse(), new Object()));
        assertNull(com.ikeu.common.context.BaseContext.getCurrentId());
    }
}
