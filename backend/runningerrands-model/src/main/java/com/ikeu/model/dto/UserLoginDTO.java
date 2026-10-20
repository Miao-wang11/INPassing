package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

@Data
@Schema(description = "用户登录请求DTO")
public class UserLoginDTO implements Serializable {

    @NotNull(message = "登录方式不能为空")
    @Schema(description = "登录方式：1-密码登录，2-验证码登录", example = "1")
    private Integer loginType;

    @Schema(description = "用户名或手机号（密码登录时必填）")
    private String username;

    @Schema(description = "密码（密码登录时必填）")
    private String password;

    @Schema(description = "手机号（验证码登录时必填）", example = "13800138000")
    private String phone;

    @Schema(description = "验证码（验证码登录时必填）", example = "123456")
    private String code;
}