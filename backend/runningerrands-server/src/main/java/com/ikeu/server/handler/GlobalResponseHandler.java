package com.ikeu.server.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikeu.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import jakarta.validation.constraints.NotNull;

/**
 * 全局响应处理器，统一包装 Controller 返回结果为 {@link Result} 格式。
 * @author ikeu
 * @since 2026/05/14
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.ikeu.server.controller")
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 判断当前响应是否需要包装。
     *
     * <p>通过检查请求 URI 是否包含 /v3/api-docs、/swagger-ui、/doc.html、
     * /webjars、/error、/imgs/ 等路径前缀来排除 Swagger 文档、错误页面和静态资源，
     * 这些路径的响应原样返回不做包装。
     *
     * @param returnType 方法返回类型
     * @param converterType 消息转换器类型
     * @return true 需要包装，false 跳过
     */
    @Override
    public boolean supports(@NotNull MethodParameter returnType,
                            @NotNull Class<? extends HttpMessageConverter<?>> converterType) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return true;
        }
        String path = attrs.getRequest().getRequestURI();
        boolean excluded = path.contains("/v3/api-docs") ||
                path.contains("/api/v3/api-docs") ||
                path.contains("/swagger-config") ||
                path.contains("/swagger-ui") ||
                path.contains("/doc.html") ||
                path.contains("/webjars") ||
                path.contains("/error") ||
                path.contains("/imgs/");
        if (excluded) {
            log.debug("Swagger 路径不包装: {}", path);
            return false;
        }
        return true;
    }

    /**
     * 在响应体写入前将结果包装为 Result 统一格式。
     *
     * <p>二次检查路径排除后按以下规则处理：
     * <ul>
     *   <li>body 已是 Result 类型 —— 直接返回不重复包装</li>
     *   <li>body 是 String —— 用 ObjectMapper 序列化为 JSON 字符串，避免直接返回 String 时
     *       StringHttpMessageConverter 类型转换错误</li>
     *   <li>其他类型 —— 调用 Result.success(body) 包装为统一的成功响应</li>
     * </ul>
     *
     * @param body 原始响应体
     * @param returnType 方法返回类型
     * @param selectedContentType 内容类型
     * @param selectedConverterType 消息转换器类型
     * @param request 请求
     * @param response 响应
     * @return 包装后的响应对象
     */
    @Override
    public Object beforeBodyWrite(Object body,
                                  @NotNull MethodParameter returnType,
                                  @NotNull MediaType selectedContentType,
                                  @NotNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NotNull ServerHttpRequest request,
                                  @NotNull ServerHttpResponse response) {
        String path = request.getURI().getPath();
        if (path.contains("/v3/api-docs") || path.contains("/swagger-config") || path.contains("/imgs/")) {
            return body;
        }

        if (body instanceof Result) {
            return body;
        }
        if (body instanceof String) {
            try {
                return objectMapper.writeValueAsString(Result.success(body));
            } catch (JsonProcessingException e) {
                log.error("响应包装字符串时发生异常", e);
                return Result.error("系统异常");
            }
        }
        return Result.success(body);
    }
}
