package com.ikeu.server.agent;

import com.ikeu.common.result.PageResult;
import com.ikeu.server.service.CampusCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP dispatch via MockMvc + actual HMAC interceptor; Java services are mocked. */
class AgentReadOnlyHttpTest {
    @Test
    void unsignedRequestFailsBeforeReachingServiceAndSignedRequestReturnsNoSensitiveFields() throws Exception {
        var wire = new AgentWireSecurity("unit-test-agent-secret-0123456789-abcdefghijk");
        var campus = mock(CampusCatalogService.class);
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(campus.requireUserCampus(12L)).thenReturn(8L);
        var service = mock(AgentReadOnlyService.class);
        when(service.searchTasks(eq(12L), eq("paper"), isNull(), eq(1), eq(10)))
                .thenReturn(new PageResult<>(1L, List.of(new AgentReadOnlyService.TaskCard(
                        31L, "校内代办", "资料打印", "公开内容", "图书馆", "宿舍",
                        null, null, null, null, java.time.LocalDateTime.now().plusHours(1)))));
        var interceptor = new AgentInternalAuthInterceptor(wire, campus, redis);
        var mvc = MockMvcBuilders.standaloneSetup(new AgentReadOnlyController(service))
                .addInterceptors(interceptor).build();
        mvc.perform(get("/internal/agent/v1/tasks/search").queryParam("keyword", "paper"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);

        String principal = wire.issuePrincipal(12L, 8L);
        String ts = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String target = "/internal/agent/v1/tasks/search?keyword=paper";
        mvc.perform(get("/internal/agent/v1/tasks/search").param("keyword", "paper")
                        .header("X-Agent-Principal", principal)
                        .header("X-Agent-Timestamp", ts)
                        .header("X-Agent-Nonce", nonce)
                        .header("X-Agent-Signature", wire.signature("GET", target, principal, ts, nonce)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].taskId").value(31))
                .andExpect(jsonPath("$.data.records[0].contactPhone").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].privateNote").doesNotExist());
    }
}
