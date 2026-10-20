package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

@Data
@Schema(description = "修改个人基本信息请求DTO")
public class UpdateProfileDTO implements Serializable {

    @Size(max = 32, message = "昵称最长32个字符")
    @Schema(description = "新昵称", example = "校园跑腿王")
    private String nickname;

    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Size(max = 32, message = "学院名最长32个字符")
    @Schema(description = "学院", example = "计算机学院")
    private String campus;

    @Schema(description = "性别：男/女")
    private String sex;

    @Size(max = 128, message = "个性签名最长128个字符")
    @Schema(description = "个性签名", example = "用最快的速度，解最急的需求")
    private String signature;
}