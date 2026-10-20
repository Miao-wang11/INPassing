package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
@Schema(description = "实名认证请求DTO")
public class RealNameAuthDTO implements Serializable {

    @NotBlank(message = "真实姓名不能为空")
    @Schema(description = "真实姓名")
    private String realName;

    @NotBlank(message = "学号不能为空")
    @Schema(description = "学号")
    private String studentId;

    @NotBlank(message = "证件照片不能为空")
    @Schema(description = "学生证照片URL")
    private String certImageUrl;
}