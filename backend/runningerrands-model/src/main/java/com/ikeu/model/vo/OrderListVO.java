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
@Schema(description = "订单列表信息VO")
public class OrderListVO implements Serializable {

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "任务ID")
    private Long taskId;

    @Schema(description = "任务大类")
    private String type;

    @Schema(description = "任务小类")
    private String subType;

    @Schema(description = "任务规格JSON")
    private String taskSpecs;

    @Schema(description = "公开任务描述")
    private String publicDesc;

    @Schema(description = "报酬")
    private BigDecimal reward;

    @Schema(description = "订单状态")
    private Integer orderStatus;

    @Schema(description = "接单时间")
    private LocalDateTime acceptTime;

    @Schema(description = "预计完成时间")
    private LocalDateTime expectFinishTime;

    @Schema(description = "确认完成时间")
    private LocalDateTime confirmTime;

    @Schema(description = "取件地址")
    private String pickupAddress;

    @Schema(description = "送达地址")
    private String deliveryAddress;

    @Schema(description = "联系人姓名")
    private String contactName;

    @Schema(description = "联系人电话")
    private String contactPhone;

    @Schema(description = "发布者昵称")
    private String publisherNickname;

    @Schema(description = "当前用户是否已评价")
    private Boolean hasReviewed;
}
