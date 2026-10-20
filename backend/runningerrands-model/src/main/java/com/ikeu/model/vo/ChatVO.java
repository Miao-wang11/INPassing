package com.ikeu.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "聊天消息VO")
public class ChatVO implements Serializable {

    @Schema(description = "消息ID")
    private Long messageId;

    @Schema(description = "发送者ID")
    private Long senderId;

    @Schema(description = "发送者昵称")
    private String senderNickname;

    @Schema(description = "发送者头像")
    private String senderAvatar;

    @Schema(description = "接收者ID")
    private Long receiverId;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息类型：1-文字 2-图片")
    private Integer messageType;

    @Schema(description = "是否已读")
    private Integer isRead;

    @Schema(description = "发送时间")
    private LocalDateTime createdAt;

    @Schema(description = "消息操作类型：0-正常消息 1-已删除 2-已撤回")
    private Integer messageAction;
}
