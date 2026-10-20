package com.ikeu.server.interceptor;

import com.ikeu.common.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器，从 URL 查询参数中提取 JWT token 并验证用户身份。
 * @author ikeu
 * @since 2026/05/26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    /**
     * 在 WebSocket 握手前从请求参数中提取 token 并验证。
     *
     * <p>校验逻辑：从 URL 参数 "token" 获取 JWT，调用 parseUserAccessToken 解析并提取 userId，
     * 验证通过后将 userId 存入 WebSocket session attributes 供后续 STOMP 通道拦截器使用。
     * 验证失败仅记录 warn 日志，不阻止握手（总是返回 true，细粒度控制在 STOMP 层）。
     *
     * @param request 服务端 HTTP 请求
     * @param response 服务端 HTTP 响应
     * @param wsHandler WebSocket 处理器
     * @param attributes WebSocket session 属性
     * @return 始终返回 true，允许握手继续
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpReq = servletRequest.getServletRequest();
            String token = httpReq.getParameter("token");
            if (token == null || token.isEmpty()) {
                log.warn("WebSocket handshake rejected: missing token");
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
            try {
                var claims = jwtUtil.parseUserAccessToken(token);
                Long userId = jwtUtil.getUserIdFromClaims(claims);
                attributes.put("userId", userId);
                log.info("WebSocket handshake authenticated: userId={}", userId);
                return true;
            } catch (Exception e) {
                log.warn("WebSocket handshake rejected: invalid token: {}", e.getMessage());
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
        }
        log.warn("WebSocket handshake rejected: unsupported request type");
        response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
        return false;
    }

    /**
     * 握手后处理，当前无操作。
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
