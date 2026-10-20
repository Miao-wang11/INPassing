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
@Schema(description = "配送员档案信息VO")
public class RunnerInfoVO implements Serializable {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "认证状态：0-未认证 1-审核中 2-已认证 3-驳回")
    private Integer verifyStatus;

    @Schema(description = "认证驳回原因")
    private String verifyRemark;

    @Schema(description = "历史总接单数")
    private Integer totalOrders;

    @Schema(description = "成功完成单数")
    private Integer successOrders;

    @Schema(description = "平均评分")
    private Double avgRating;

    @Schema(description = "信用分")
    private Integer creditScore;

    @Schema(description = "是否被禁止接单：0-未禁止 1-已禁止")
    private Integer isBanned;

    @Schema(description = "是否在线接单：0-离线 1-在线")
    private Integer isOnline;

    @Schema(description = "最大同时接单数")
    private Integer maxConcurrentOrders;

    @Schema(description = "当前进行中的订单数")
    private Integer currentOrders;

    @Schema(description = "禁止接单截止时间（信用分冻结）")
    private LocalDateTime banUntil;
}