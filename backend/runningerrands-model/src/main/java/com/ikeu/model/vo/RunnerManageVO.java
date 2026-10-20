package com.ikeu.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理员配送员管理VO")
public class RunnerManageVO implements Serializable {

    @Schema(description = "配送员档案ID")
    private Long profileId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "认证状态：0-未认证 1-审核中 2-已认证 3-驳回")
    private Integer verifyStatus;

    @Schema(description = "信用分")
    private Integer creditScore;

    @Schema(description = "历史总接单数")
    private Integer totalOrders;

    @Schema(description = "成功完成单数")
    private Integer successOrders;

    @Schema(description = "平均评分")
    private Double avgRating;

    @Schema(description = "是否在线：0-离线 1-在线")
    private Integer isOnline;

    @Schema(description = "当前进行中订单数")
    private Integer currentOrders;

    @Schema(description = "是否禁止接单：0-正常 1-禁止")
    private Integer isBanned;

    @Schema(description = "最大同时接单数")
    private Integer maxConcurrentOrders;

    @Schema(description = "累计收入")
    private java.math.BigDecimal totalEarnings;
}
