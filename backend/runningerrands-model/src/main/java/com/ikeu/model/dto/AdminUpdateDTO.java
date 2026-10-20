package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "更新管理员信息请求DTO")
public class AdminUpdateDTO implements Serializable {

    @NotBlank(message = "姓名不能为空")
    @Schema(description = "姓名")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "性别")
    private String sex;

    @NotNull(message = "角色不能为空")
    @Schema(description = "角色：1-超管 2-运营")
    private Integer role;
}
