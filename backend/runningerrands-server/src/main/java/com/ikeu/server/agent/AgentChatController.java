package com.ikeu.server.agent;

import com.ikeu.common.context.BaseContext;
import com.ikeu.server.service.CampusCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikeu.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * User-facing Agent entry. Existing user JWT interceptor authenticates /agent/**.
 * It does NOT expose the Python endpoint directly to uni-app.
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentChatController {
    private final CampusCatalogService campuses;
    private final AgentWireSecurity wire;
    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Value("${runningerrands.agent.python-url:http://127.0.0.1:8000}")
    private String pythonUrl;

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chat(@RequestBody String body) {
        return forward(body, "/internal/v1/delegation");
    }

    @PostMapping(value = "/route-match", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> routeMatch(@RequestBody String body) {
        return forward(body, "/internal/v1/route-match");
    }

    private ResponseEntity<String> forward(String body, String path) {
        if (body == null || body.isBlank() || body.length() > 8000) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"code\":0,\"msg\":\"invalid chat payload\"}");
        }
        if (!wire.configured()) {
            return ResponseEntity.status(503).contentType(MediaType.APPLICATION_JSON)
                    .body("{\"code\":0,\"msg\":\"AI service disabled\"}");
        }

        // Only approved, enabled students with an active campus can access the Agent.
        Long uid = BaseContext.getCurrentId();
        Long campusId = campuses.requireUserCampus(uid);
        String principal = wire.issuePrincipal(uid, campusId);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        String requestId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        String sha256;
        try {
            sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
        String signature = wire.signature("POST", path + "#" + sha256, principal, timestamp, nonce);
        try {
            URI endpoint = URI.create(pythonUrl.replaceAll("/+$", "") + path);
            if (!("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))) {
                throw new IllegalArgumentException("Agent endpoint scheme not allowed");
            }
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("X-Agent-Principal", principal)
                    .header("X-Agent-Timestamp", timestamp)
                    .header("X-Agent-Nonce", nonce)
                    .header("X-Agent-Signature", signature)
                    .header("X-Request-ID", requestId)
                    .header("X-Trace-ID", traceId)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> reply = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Agent chat proxy status={} requestId={} traceId={}", reply.statusCode(), requestId, traceId);
            // uni-app shared request.js expects {code:1,data:...}, not raw FastAPI JSON.
            // Never treat a failed/unparseable Python response as successful business data.
            if (reply.statusCode() != 200) {
                String message = reply.statusCode() == 422
                        ? "智能请求字段不完整，请核对后重试" : "智能服务暂时不可用，请稍后重试";
                return ResponseEntity.status(reply.statusCode() == 422 ? 400 : 503)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(Result.error(message)));
            }
            JsonNode payload = objectMapper.readTree(reply.body());
            if (payload == null || !payload.isObject() || !payload.hasNonNull("status")
                    || !payload.hasNonNull("session_id")) {
                return ResponseEntity.status(502).contentType(MediaType.APPLICATION_JSON)
                        .body(objectMapper.writeValueAsString(Result.error("智能服务响应格式异常")));
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(Result.success(payload)));
        } catch (InterruptedException ex) {
            log.warn("Agent chat interrupted requestId={} traceId={}", requestId, traceId);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(503).contentType(MediaType.APPLICATION_JSON)
                    .body("{\"code\":0,\"msg\":\"AI service unavailable\"}");
        } catch (Exception ex) {
            log.warn("Agent chat unavailable requestId={} traceId={}", requestId, traceId);
            // Never echo Python URL or internal exception details.
            return ResponseEntity.status(503).contentType(MediaType.APPLICATION_JSON)
                    .body("{\"code\":0,\"msg\":\"AI service unavailable\"}");
        }
    }
}
