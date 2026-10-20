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
@Schema(description = "用户地址返回VO")
public class AddressVO implements Serializable {

    @Schema(description = "地址ID")
    private Long id;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "性别")
    private String sex;

    @Schema(description = "详细地址")
    private String detail;

    @Schema(description = "是否默认地址（1-是，0-否）")
    private Integer isDefault;

    @Schema(description = "地址经度")
    private BigDecimal lng;

    @Schema(description = "地址纬度")
    private BigDecimal lat;
}
