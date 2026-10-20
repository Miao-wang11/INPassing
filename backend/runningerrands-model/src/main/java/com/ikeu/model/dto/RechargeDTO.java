package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Schema(description = "用户充值/提现请求DTO")
public class RechargeDTO implements Serializable {

    @NotNull
    @DecimalMin(value = "0.01", message = "金额必须大于0")
    @Schema(description = "充值/提现金额（元）", example = "50.00")
    private BigDecimal amount;

    @NotBlank(message = "支付密码不能为空")
    @Schema(description = "支付密码")
    private String payPassword;
}
