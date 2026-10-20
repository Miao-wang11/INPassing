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
@Schema(description = "通知信息VO")
public class NotificationVO implements Serializable {

    @Schema(description = "通知ID")
    private Long id;

    @Schema(description = "通知类型")
    private Integer type;

    @Schema(description = "通知标题")
    private String title;

    @Schema(description = "通知内容")
    private String content;

    @Schema(description = "是否已读：0-未读 1-已读")
    private Integer isRead;

    @Schema(description = "关联业务ID")
    private Long targetId;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
