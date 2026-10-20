package com.ikeu.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Schema(description = "上传凭证请求DTO")
public class ProofImageDTO implements Serializable {

    @Schema(description = "凭证图片URL")
    @NotEmpty(message = "至少上传一张凭证图片")
    private List<String> imageUrls;

}