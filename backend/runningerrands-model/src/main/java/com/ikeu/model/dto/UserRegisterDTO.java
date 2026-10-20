package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;

@Data
@Schema(description = "用户注册请求DTO")
public class UserRegisterDTO implements Serializable {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "短信验证码", example = "123456")
    private String code;

    @NotBlank(message = "用户名不能为空")
    @Schema(description = "用户名", example = "kecheng123")
    private String username;

    @Schema(description = "性别：男/女")
    private String sex;

    @NotBlank(message = "密码不能为空")
    @Schema(description = "密码", example = "123456")
    private String password;
}