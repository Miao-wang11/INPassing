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
@Schema(description = "交易资金流水记录VO")
public class TransactionVO implements Serializable {

    @Schema(description = "交易流水记录ID")
    private Long id;

    @Schema(description = "关联的用户ID")
    private Long userId;

    @Schema(description = "用户昵称")
    private String userNickname;

    @Schema(description = "关联的任务ID")
    private Long taskId;

    @Schema(description = "变动金额（正为收入，负为支出）")
    private BigDecimal amount;

    @Schema(description = "类型：1-任务悬赏支出 2-跑腿收入 3-充值 4-提现 5-退款")
    private Integer type;

    @Schema(description = "变动前余额")
    private BigDecimal balanceBefore;

    @Schema(description = "变动后余额")
    private BigDecimal balanceAfter;

    @Schema(description = "发生时间")
    private LocalDateTime createdAt;
}
