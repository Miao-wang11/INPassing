package com.ikeu.server.config;

import com.ikeu.server.interceptor.AuthChannelInterceptor;
import com.ikeu.server.interceptor.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置，注册 STOMP 端点、消息代理和通道线程池。
 * @author ikeu
 * @since 2025/05/21
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    private final AuthChannelInterceptor authChannelInterceptor;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    /**
     * 注册 STOMP 端点，同时暴露 SockJS 和原生 WebSocket 两种接入方式。
     *
     * <p>为 /ws/notification 和 /ws/chat 两个端点分别注册 SockJS 和原生 WebSocket，
     * 均添加 {@link JwtHandshakeInterceptor} 在握手阶段进行 JWT 认证，
     * 允许所有来源的跨域请求。
     *
     * @param registry STOMP 端点注册器
     */
    @Override
    public void registerStompEndpoints(@NotNull StompEndpointRegistry registry) {
        log.info("开始注册STOMP端点...");
        registry.addEndpoint("/ws/notification").setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor).withSockJS();
        registry.addEndpoint("/ws/chat").setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor).withSockJS();

        registry.addEndpoint("/ws/chat").setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor);
        registry.addEndpoint("/ws/notification").setAllowedOriginPatterns("*")
                .addInterceptors(jwtHandshakeInterceptor);
    }

    /**
     * 配置消息代理，应用目标前缀为 /app，启用 /user 和 /queue 简单代理。
     *
     * @param registry 消息代理注册器
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/user", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * 配置客户端入站通道，添加 {@link AuthChannelInterceptor} 认证拦截器并设置线程池。
     *
     * @param registration 通道注册器
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
        registration.taskExecutor(stompTaskExecutor());
    }

    /**
     * 配置客户端出站通道的线程池。
     *
     * @param registration 通道注册器
     */
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(stompTaskExecutor());
    }

    /**
     * 创建 STOMP 通道线程池，配置优雅关闭避免 shutdown 时抛 RejectedExecutionException。
     *
     * <p>核心线程 2，最大线程 10，队列容量 0（溢满时直接创建新线程），
     * shutdown 时等待队列任务完成（最多 10 秒）。
     *
     * @return ThreadPoolTaskExecutor 实例
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor stompTaskExecutor() {
        log.info("创建STOMP通道线程池...");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(0);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setThreadNamePrefix("stomp-exec-");
        return executor;
    }
}
