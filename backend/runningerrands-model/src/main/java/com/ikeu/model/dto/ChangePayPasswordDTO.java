package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "修改支付密码请求DTO（已设置过支付密码时使用）")
public class ChangePayPasswordDTO implements Serializable {

    @NotBlank(message = "原支付密码不能为空")
    @Schema(description = "原支付密码")
    private String oldPayPassword;

    @NotBlank(message = "新支付密码不能为空")
    @Schema(description = "新支付密码")
    private String newPayPassword;
}
