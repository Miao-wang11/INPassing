package com.ikeu.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "runningerrands.alioss")
public class AliOssProperties {

    private String accessKeyId;
    private String accessKeySecret;
    private String endpoint;
    private String bucketName;

}
