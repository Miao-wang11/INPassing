package com.ikeu.server.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikeu.common.context.BaseContext;
import com.ikeu.server.service.CampusCatalogService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercise Java's REAL HTTP client against a local TCP FastAPI-shaped stub. */
class AgentProxyEnvelopeTest {
    @Test
    void successIsWrappedIntoExistingUniAppResponseEnvelopeAndErrorsFailClosed() throws Exception {
        var campus = mock(CampusCatalogService.class);
        when(campus.requireUserCampus(9L)).thenReturn(7L);
        var wire = new AgentWireSecurity("integration-only-secret-0123456789-abcdef0123456789");
        var controller = new AgentChatController(campus, wire, new ObjectMapper());
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicBoolean authSeen = new AtomicBoolean(false);
        server.createContext("/internal/v1/delegation", req -> {
            authSeen.set(req.getRequestHeaders().getFirst("X-Agent-Signature") != null
                    && req.getRequestHeaders().getFirst("X-Agent-Principal") != null);
            byte[] bytes = "{\"session_id\":\"5cbde06b-a0ff-4c1e-9a2b-995119ce991f\",\"status\":\"needs_clarification\",\"answer\":\"继续输入\"}"
                    .getBytes(StandardCharsets.UTF_8);
            req.getResponseHeaders().add("Content-Type", "application/json");
            req.sendResponseHeaders(200, bytes.length);
            try (var output=req.getResponseBody()) { output.write(bytes); }
        });
        server.createContext("/internal/v1/route-match", req -> {
            byte[] bytes = "{\"detail\":\"Never leak Python traceback or secret\"}"
                    .getBytes(StandardCharsets.UTF_8);
            req.sendResponseHeaders(422, bytes.length);
            try (var output=req.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        BaseContext.setCurrentId(9L);
        try {
            ReflectionTestUtils.setField(controller,"pythonUrl",
                    "http://127.0.0.1:"+server.getAddress().getPort());
            var ok=controller.chat("{\"message\":\"打印资料\"}");
            assertEquals(200,ok.getStatusCode().value());
            var parsed = new ObjectMapper().readTree(ok.getBody());
            assertEquals(1, parsed.path("code").asInt());
            assertEquals("needs_clarification",parsed.path("data").path("status").asText());
            assertTrue(authSeen.get());

            var bad=controller.routeMatch("{\"message\":\"图书馆到宿舍\"}");
            assertEquals(400,bad.getStatusCode().value());
            var error=new ObjectMapper().readTree(bad.getBody());
            assertEquals(0,error.path("code").asInt());
            assertFalse(bad.getBody().contains("Never leak"));
            assertFalse(bad.getBody().contains("traceback"));
        } finally {
            BaseContext.removeCurrentId();
            server.stop(0);
        }
    }
}
