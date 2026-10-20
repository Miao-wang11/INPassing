package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "发送聊天消息DTO")
public class ChatSendDTO implements Serializable {

    @NotNull(message = "接收者ID不能为空")
    @Schema(description = "接收者用户ID")
    private Long receiverId;

    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息类型：1-文字 2-图片")
    private Integer messageType;
}
