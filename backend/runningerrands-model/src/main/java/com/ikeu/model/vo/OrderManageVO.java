package com.ikeu.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "管理员订单管理VO")
public class OrderManageVO implements Serializable {

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "任务编号")
    private String taskNo;

    @Schema(description = "公开任务描述")
    private String publicDesc;

    @Schema(description = "报酬")
    private BigDecimal reward;

    @Schema(description = "发布者ID")
    private Long publisherId;

    @Schema(description = "发布者昵称")
    private String publisherNickname;

    @Schema(description = "跑腿员ID")
    private Long runnerId;

    @Schema(description = "跑腿员昵称")
    private String runnerNickname;

    @Schema(description = "订单状态")
    private Integer status;

    @Schema(description = "接单时间")
    private LocalDateTime acceptTime;

    @Schema(description = "取货时间")
    private LocalDateTime pickupTime;

    @Schema(description = "送达时间")
    private LocalDateTime deliverTime;

    @Schema(description = "确认完成时间")
    private LocalDateTime confirmTime;

    @Schema(description = "预计送达时间")
    private LocalDateTime expectFinishTime;
}
