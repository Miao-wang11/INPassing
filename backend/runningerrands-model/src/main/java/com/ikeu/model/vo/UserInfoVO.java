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
@Schema(description = "用户信息VO")
public class UserInfoVO implements Serializable {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "头像URL")
    private String avatarUrl;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "性别")
    private String sex;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "学号")
    private String studentId;

    @Schema(description = "认证照片URL")
    private String certifyImg;

    @Schema(description = "认证审核备注")
    private String certifyRemark;

    @Schema(description = "账户余额")
    private BigDecimal balance;

    @Schema(description = "认证状态：0-未认证 1-审核中 2-已认证 3-认证驳回")
    private Integer isCertify;

    @Schema(description = "跑腿员认证状态：0-未认证 1-审核中 2-已认证 3-认证驳回")
    private Integer verifyStatus;

    @Schema(description = "注册类型：1-手机号 2-微信")
    private Integer registerType;

    @Schema(description = "学院")
    private String campus;

    @Schema(description = "个性签名")
    private String signature;

    @Schema(description = "账户状态：0-禁用 1-正常")
    private Integer status;

    @Schema(description = "注册时间")
    private LocalDateTime createdAt;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "信用分（仅在跑腿员认证后有效）")
    private Integer creditScore;

    @Schema(description = "禁止接单截止时间（信用分冻结）")
    private LocalDateTime banUntil;
}
