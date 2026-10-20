package com.ikeu.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "配送员排行榜VO")
public class RunnerRankingVO implements Serializable {

    @Schema(description = "排名")
    private Long rank;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatarUrl;

    @Schema(description = "总接单数")
    private int totalOrders;

    @Schema(description = "成功完成数")
    private int successOrders;

    @Schema(description = "完成率(%)")
    private double completionRate;

    @Schema(description = "平均评分")
    private double avgRating;

    @Schema(description = "总收入")
    private BigDecimal totalEarnings;
}
