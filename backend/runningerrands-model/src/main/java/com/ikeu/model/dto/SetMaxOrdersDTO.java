package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

@Data
@Schema(description = "设置最大接单数请求DTO")
public class SetMaxOrdersDTO implements Serializable {

    @NotNull(message = "最大接单数不能为空")
    @Min(value = 1, message = "最大接单数至少为1")
    @Max(value = 5, message = "最大接单数不能超过5")
    @Schema(description = "最大同时接单数（1-5）", example = "3")
    private Integer maxOrders;
}