package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "接单员取消订单请求DTO")
public class CancelOrderDTO implements Serializable {

    @Schema(description = "订单取消原因")
    private String reason;
}
