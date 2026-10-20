package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "全站广播通知请求DTO")
public class NotificationBroadcastDTO implements Serializable {

    @NotNull(message = "通知类型不能为空")
    @Schema(description = "通知类型")
    private Integer type;

    @NotBlank(message = "标题不能为空")
    @Schema(description = "通知标题")
    private String title;

    @NotBlank(message = "内容不能为空")
    @Schema(description = "通知内容")
    private String content;
}
