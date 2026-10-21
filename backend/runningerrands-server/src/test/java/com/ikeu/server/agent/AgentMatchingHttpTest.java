package com.ikeu.server.agent;

import com.ikeu.server.service.CampusCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Signed Java internal HTTP: identity from HMAC, not client query or LLM. */
class AgentMatchingHttpTest {
    @Test
    void unsignedTamperedAndReplayedMatchingApiCallsFailClosed() throws Exception {
        var wire = new AgentWireSecurity("agent-match-test-shared-secret-0123456789");
        var campuses = mock(CampusCatalogService.class);
        when(campuses.requireUserCampus(9L)).thenReturn(7L);
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> ops=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(),eq("1"),any(Duration.class)))
                .thenReturn(true).thenReturn(false);
        var tasks=mock(AgentReadOnlyService.class);
        var network=mock(AgentWalkNetworkService.class);
        when(tasks.matchingCandidates(9L,1,20)).thenReturn(
                new AgentReadOnlyService.CandidateBatch(0,0,false,List.of()));
        var mvc=MockMvcBuilders.standaloneSetup(new AgentMatchingReadController(tasks,network))
                .addInterceptors(new AgentInternalAuthInterceptor(wire,campuses,redis)).build();
        String path="/internal/agent/v1/matching/candidates";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(tasks);
        String token=wire.issuePrincipal(9L,7L),ts=Long.toString(Instant.now().getEpochSecond());
        String nonce=UUID.randomUUID().toString(),signature=wire.signature("GET",path,token,ts,nonce);
        mvc.perform(get(path).header("X-Agent-Principal",token)
                .header("X-Agent-Timestamp",ts).header("X-Agent-Nonce",nonce)
                .header("X-Agent-Signature",signature))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.examined").value(0));
        mvc.perform(get(path).header("X-Agent-Principal",token)
                .header("X-Agent-Timestamp",ts).header("X-Agent-Nonce",nonce)
                .header("X-Agent-Signature",signature))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-Agent-Principal",token)
                .header("X-Agent-Timestamp",ts).header("X-Agent-Nonce",UUID.randomUUID().toString())
                .header("X-Agent-Signature",signature))
                .andExpect(status().isUnauthorized());
        verify(tasks,times(1)).matchingCandidates(9L,1,20);
    }
}
