package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Schema(description = "用户地址保存请求DTO")
public class AddressSaveDTO implements Serializable {

    @NotBlank(message = "联系人不能为空")
    @Schema(description = "联系人", example = "张三")
    private String contactName;

    @NotBlank(message = "联系电话不能为空")
    @Schema(description = "联系电话", example = "13800138000")
    private String contactPhone;

    @Schema(description = "性别", example = "男")
    private String sex;

    @NotBlank(message = "详细地址不能为空")
    @Schema(description = "详细地址，如：XX宿舍楼XXX室", example = "3号宿舍楼501室")
    private String detail;

    @Schema(description = "是否设置为默认地址（1-是，0-否）", example = "0")
    private Integer isDefault;

    @Schema(description = "地址经度（地图选点）")
    private BigDecimal lng;

    @Schema(description = "地址纬度（地图选点）")
    private BigDecimal lat;
}
