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
@Schema(description = "任务统计VO")
public class TaskStatisticsVO implements Serializable {

    @Schema(description = "总发布数")
    private long totalPublished;

    @Schema(description = "待接单数")
    private long waitingCount;

    @Schema(description = "进行中数(已接单+配送中+待确认)")
    private long inProgressCount;

    @Schema(description = "已完成数")
    private long completedCount;

    @Schema(description = "已取消数")
    private long cancelledCount;
}
