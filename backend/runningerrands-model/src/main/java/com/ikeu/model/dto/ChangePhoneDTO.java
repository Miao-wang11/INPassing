package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;

@Data
@Schema(description = "修改手机号请求DTO")
public class ChangePhoneDTO implements Serializable {

    @NotBlank(message = "新手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "新手机号", example = "13900139000")
    private String newPhone;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "新手机号收到的短信验证码", example = "123456")
    private String code;
}