package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "用户取消任务请求DTO")
public class CancelTaskDTO implements Serializable {

    @Schema(description = "任务取消原因")
    private String reason;
}