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
@Schema(description = "管理员登录响应VO")
public class AdminLoginVO implements Serializable {

    @Schema(description = "管理员ID")
    private Long adminId;

    @Schema(description = "管理员用户名")
    private String username;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "角色：1-超管 2-运营 3-财务")
    private Integer role;

    @Schema(description = "JWT访问令牌")
    private String token;

    @Schema(description = "JWT刷新令牌")
    private String refreshToken;

    @Schema(description = "访问令牌过期时间（秒）")
    private Long expiresIn;
}
