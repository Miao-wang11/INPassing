package com.ikeu.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "评价信息VO")
public class ReviewVO implements Serializable {

    @Schema(description = "评价ID")
    private Long reviewId;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "评价人ID")
    private Long reviewerId;

    @Schema(description = "评价人昵称")
    private String reviewerNickname;

    @Schema(description = "评价人头像")
    private String reviewerAvatar;

    @Schema(description = "评分")
    private Integer rating;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "评价标签")
    private List<String> tags;

    @Schema(description = "父评价ID")
    private Long parentId;

    @Schema(description = "追加评价列表")
    private List<ReviewVO> followUps;

    @Schema(description = "评价时间")
    private LocalDateTime createdAt;
}