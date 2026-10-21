package com.ikeu.server.agent;

import com.ikeu.common.result.PageResult;
import com.ikeu.server.service.CampusCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual localhost TCP HTTP against a minimal embedded Spring application, with real interceptor. */
@SpringBootTest(
        classes = AgentTcpHttpTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.servlet.context-path=/api",
                "runningerrands.agent.shared-secret=unit-test-agent-secret-0123456789-abcdefghijk"
        })
class AgentTcpHttpTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class
    })
    @Import({AgentWireSecurity.class, AgentInternalAuthInterceptor.class, AgentReadOnlyController.class})
    static class TestApp {
        @Bean CampusCatalogService campuses() { return mock(CampusCatalogService.class); }
        @Bean AgentReadOnlyService readOnlyService() { return mock(AgentReadOnlyService.class); }
        @Bean StringRedisTemplate replayRedis() { return mock(StringRedisTemplate.class); }
        @Bean WebMvcConfigurer agentInterceptor(AgentInternalAuthInterceptor interceptor) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(interceptor).addPathPatterns("/internal/agent/**");
                }
            };
        }
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired AgentWireSecurity wire;
    @Autowired CampusCatalogService campuses;
    @Autowired AgentReadOnlyService service;
    @Autowired StringRedisTemplate redis;

    @Test
    void authenticatesActualHttpChecksReplayAndRevalidatesCampus() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(true).thenReturn(false).thenReturn(true);
        when(campuses.requireUserCampus(12L)).thenReturn(8L).thenReturn(9L);
        when(service.searchTasks(eq(12L), eq("paper"), isNull(), eq(1), eq(10)))
                .thenReturn(new PageResult<>(1L, List.of(new AgentReadOnlyService.TaskCard(
                        33L, "校内代办", "资料打印", "打印", "图书馆", "宿舍",
                        null, null, null, null, java.time.LocalDateTime.now().plusHours(1)))));

        String raw = "/api/internal/agent/v1/tasks/search?keyword=paper";
        String url = "http://127.0.0.1:" + port + raw;
        assertEquals(401, http.getForEntity(url, String.class).getStatusCode().value());

        String principal = wire.issuePrincipal(12L, 8L);
        String ts = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        HttpHeaders signed = new HttpHeaders();
        signed.add("X-Agent-Principal", principal);
        signed.add("X-Agent-Timestamp", ts);
        signed.add("X-Agent-Nonce", nonce);
        signed.add("X-Agent-Signature", wire.signature("GET", raw, principal, ts, nonce));
        var first = http.exchange(url, HttpMethod.GET, new HttpEntity<>(signed), String.class);
        assertEquals(200, first.getStatusCode().value(), first.getBody());
        assertNotNull(first.getBody());
        assertTrue(first.getBody().contains("\"taskId\":33"), first.getBody());
        assertFalse(first.getBody().contains("contactPhone"), first.getBody());
        assertEquals(401, http.exchange(url, HttpMethod.GET, new HttpEntity<>(signed), String.class)
                .getStatusCode().value());
        HttpHeaders second = new HttpHeaders();
        String nonce2 = UUID.randomUUID().toString();
        second.add("X-Agent-Principal", principal);
        second.add("X-Agent-Timestamp", ts);
        second.add("X-Agent-Nonce", nonce2);
        second.add("X-Agent-Signature", wire.signature("GET", raw, principal, ts, nonce2));
        assertEquals(401, http.exchange(url, HttpMethod.GET, new HttpEntity<>(second), String.class)
                .getStatusCode().value());
        verify(service, times(1)).searchTasks(12L, "paper", null, 1, 10);
    }
}
