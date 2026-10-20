package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Schema(description = "向指定用户发送通知请求DTO")
public class NotificationSendDTO implements Serializable {

    @NotEmpty(message = "用户ID列表不能为空")
    @Schema(description = "目标用户ID列表")
    private List<Long> userIds;

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
