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
@Schema(description = "用户实名认证状态VO")
public class CertifyStatusVO implements Serializable {

    @Schema(description = "用户认证状态（来自 user.is_certify）：0-未认证 1-审核中 2-已认证 3-认证驳回")
    private Integer isCertify;

    @Schema(description = "认证审核状态：0-未认证 1-审核中 2-已认证 3-认证驳回")
    private Integer verifyStatus;

    @Schema(description = "审核备注（驳回原因）")
    private String verifyRemark;

    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "学号")
    private String studentId;

    @Schema(description = "学生证照片URL")
    private String certifyImg;
}