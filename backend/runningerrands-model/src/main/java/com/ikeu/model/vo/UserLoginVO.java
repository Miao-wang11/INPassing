package com.ikeu.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户注册/登录响应VO")
public class UserLoginVO implements Serializable {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatarUrl;

    @Schema(description = "JWT访问令牌")
    private String token;

    @Schema(description = "JWT刷新令牌")
    private String refreshToken;

    @Schema(description = "访问令牌过期时间（秒）")
    private Long expiresIn;
}
