package com.ikeu.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "runningerrands.jwt")
public class JwtProperties {

    /** 管理端 JWT 签名密钥（HMAC-SHA） */
    private String adminSecretKey;

    /** 管理端前端传递 access token 的 HTTP 头名称 */
    private String adminTokenName;

    /** 用户端 JWT 签名密钥（HMAC-SHA） */
    private String userSecretKey;

    /** 用户端前端传递 access token 的 HTTP 头名称 */
    private String userTokenName;

    /** 管理端 access token 过期时间（毫秒），默认 2 小时 */
    private Long adminAccessTtl;

    /** 用户端 access token 过期时间（毫秒），默认 2 小时 */
    private Long userAccessTtl;

    /** 用户端 refresh token 过期时间（毫秒），默认 7 天 */
    private Long userRefreshTtl;

    /** 管理端 refresh token 过期时间（毫秒），默认 7 天 */
    private Long adminRefreshTtl;

}
