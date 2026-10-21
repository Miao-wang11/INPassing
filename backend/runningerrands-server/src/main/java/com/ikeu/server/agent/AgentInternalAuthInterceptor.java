package com.ikeu.server.agent;

import com.ikeu.common.context.BaseContext;
import com.ikeu.server.service.CampusCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/** Authenticates every Java read-only Agent API call, including unmatched paths. */
@Component
@RequiredArgsConstructor
public class AgentInternalAuthInterceptor implements HandlerInterceptor {
    private final AgentWireSecurity security;
    private final CampusCatalogService campuses;
    private final StringRedisTemplate redis;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            String path = request.getRequestURI();
            if (request.getQueryString() != null) path += "?" + request.getQueryString();
            String principal = request.getHeader("X-Agent-Principal");
            String timestamp = request.getHeader("X-Agent-Timestamp");
            String nonce = request.getHeader("X-Agent-Nonce");
            AgentWireSecurity.Principal actor = security.verifyRequest(
                    request.getMethod(), path, principal, timestamp, nonce,
                    request.getHeader("X-Agent-Signature"));

            // Redis prevents a captured signed read request being replayed across Java replicas.
            String replayKey = "agent:auth:v1:" + nonce;
            Boolean accepted = redis.opsForValue().setIfAbsent(replayKey, "1", Duration.ofMinutes(4));
            if (!Boolean.TRUE.equals(accepted)) throw new SecurityException("replayed service request");

            Long currentCampus = campuses.requireUserCampus(actor.userId());
            if (!currentCampus.equals(actor.campusId())) throw new SecurityException("campus context changed");
            BaseContext.setCurrentId(actor.userId());
            return true;
        } catch (Exception ex) {
            BaseContext.removeCurrentId();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }
}
