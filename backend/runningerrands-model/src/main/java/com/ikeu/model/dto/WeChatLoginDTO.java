package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "微信小程序登录请求DTO")
public class WeChatLoginDTO implements Serializable {

    @NotBlank(message = "微信临时登录凭证不能为空")
    @Schema(description = "wx.login()返回的code", example = "081xxxxx")
    private String code;

    @Schema(description = "用户信息加密数据（可选，用于获取微信头像昵称）")
    private String encryptedData;

    @Schema(description = "加密算法的初始向量（可选）")
    private String iv;
}
