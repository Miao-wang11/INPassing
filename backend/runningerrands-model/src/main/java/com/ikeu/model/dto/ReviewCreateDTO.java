package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

@Data
@Schema(description = "创建评价请求DTO")
public class ReviewCreateDTO implements Serializable {

    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    @NotNull(message = "评分不能为空")
    @Min(1) @Max(5)
    private Integer rating;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "评价标签")
    private List<String> tags;

    @Schema(description = "父评价ID（有值表示追加评价）")
    private Long parentId;
}